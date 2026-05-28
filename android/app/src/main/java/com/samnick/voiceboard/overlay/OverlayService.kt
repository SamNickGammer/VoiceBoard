package com.samnick.voiceboard.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.samnick.voiceboard.MainActivity
import com.samnick.voiceboard.R
import com.samnick.voiceboard.a11y.A11yBus
import com.samnick.voiceboard.core.ModeProcessor
import com.samnick.voiceboard.core.PrefsBridge
import com.samnick.voiceboard.transcription.TranscriptionRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OverlayService : Service() {

  companion object {
    private const val TAG = "VoiceBoardOverlay"
    private const val NOTIFICATION_ID = 4319
    private const val CHANNEL_ID = "voiceboard_overlay"

    const val ACTION_START = "com.samnick.voiceboard.overlay.START"
    const val ACTION_STOP = "com.samnick.voiceboard.overlay.STOP"

    fun start(context: Context) {
      val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
      else context.startService(intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
      context.startService(intent)
    }

    @Volatile var running: Boolean = false
      private set
  }

  private lateinit var wm: WindowManager
  private var pill: OverlayPillView? = null
  private var pillParams: WindowManager.LayoutParams? = null
  private var modeMenu: ModeMenuView? = null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var transcribeJob: Job? = null
  private var capture: AudioCapture? = null
  private var captureFile: File? = null
  private var isRecording = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> { stopOverlay(); return START_NOT_STICKY }
    }
    if (!Settings.canDrawOverlays(this)) {
      Log.w(TAG, "Overlay permission missing, refusing to start.")
      stopSelf()
      return START_NOT_STICKY
    }
    startForegroundCompat()
    ensurePill()
    PrefsBridge.setOverlayEnabled(this, true)
    running = true
    return START_STICKY
  }

  override fun onDestroy() {
    running = false
    PrefsBridge.setOverlayEnabled(this, false)
    transcribeJob?.cancel()
    scope.cancel()
    try { capture?.stop() } catch (_: Throwable) {}
    removePill()
    removeModeMenu()
    super.onDestroy()
  }

  private fun startForegroundCompat() {
    val tap = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle(getString(R.string.overlay_notification_title))
        .setContentText(getString(R.string.overlay_notification_text))
        .setContentIntent(tap)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
          NOTIFICATION_ID,
          notif,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
              ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else if (Build.VERSION.SDK_INT >= 29) {
      startForeground(
          NOTIFICATION_ID,
          notif,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notif)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < 26) return
    val mgr = getSystemService(NotificationManager::class.java) ?: return
    if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
    val ch = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.overlay_notification_channel),
        NotificationManager.IMPORTANCE_LOW,
    )
    ch.description = "Lets VoiceBoard's floating mic stay active while you dictate."
    mgr.createNotificationChannel(ch)
  }

  private fun ensurePill() {
    if (pill != null) return
    val view = OverlayPillView(this)
    val (savedX, savedY) = PrefsBridge.getOverlayPosition(this)
    val defaultY = (resources.displayMetrics.heightPixels * 0.72f).toInt()
    val widthPx = dp(120f).toInt()
    val heightPx = dp(36f).toInt()
    val params = WindowManager.LayoutParams(
        widthPx,
        heightPx,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = if (savedX != Int.MIN_VALUE) savedX
          else (resources.displayMetrics.widthPixels - widthPx) / 2
      y = if (savedY != Int.MIN_VALUE) savedY else defaultY
    }
    view.listener = pillListener
    try {
      wm.addView(view, params)
    } catch (t: Throwable) {
      Log.e(TAG, "addView failed", t)
      stopSelf()
      return
    }
    pill = view
    pillParams = params
  }

  private fun removePill() {
    pill?.let { v -> try { wm.removeView(v) } catch (_: Throwable) {} }
    pill = null
    pillParams = null
  }

  private val pillListener = object : OverlayPillView.Listener {
    override fun onTap() { toggleRecording() }

    override fun onLongPress() { showModeMenu() }

    override fun onDrag(dx: Float, dy: Float) {
      val v = pill ?: return
      val p = pillParams ?: return
      p.x = (p.x + dx).toInt().coerceIn(0, resources.displayMetrics.widthPixels - v.width)
      p.y = (p.y + dy).toInt().coerceIn(0, resources.displayMetrics.heightPixels - v.height)
      try { wm.updateViewLayout(v, p) } catch (_: Throwable) {}
    }

    override fun onDragEnd() {
      val p = pillParams ?: return
      PrefsBridge.setOverlayPosition(this@OverlayService, p.x, p.y)
    }
  }

  private fun overlayType(): Int =
      if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

  private fun toggleRecording() {
    if (isRecording) stopRecordingAndProcess() else startRecording()
  }

  private fun startRecording() {
    if (isRecording) return
    val out = File(cacheDir, "voiceboard-${System.currentTimeMillis()}.wav")
    val cap = AudioCapture(out)
    try {
      cap.start()
    } catch (t: Throwable) {
      toast("Mic unavailable: ${t.message}")
      return
    }
    capture = cap
    captureFile = out
    isRecording = true
    pill?.setState(OverlayPillView.State.RECORDING)
  }

  private fun stopRecordingAndProcess() {
    val cap = capture ?: return
    val file = captureFile ?: return
    capture = null; captureFile = null
    val byteCount = try { cap.stop() } catch (_: Throwable) { 0 }
    isRecording = false
    pill?.setState(OverlayPillView.State.THINKING)

    if (byteCount < 16_000) { // less than ~0.5 s of audio
      pill?.setState(OverlayPillView.State.IDLE)
      toast("Too short, try again")
      try { file.delete() } catch (_: Throwable) {}
      return
    }

    transcribeJob?.cancel()
    transcribeJob = scope.launch {
      try {
        val raw = withContext(Dispatchers.IO) {
          TranscriptionRouter.transcribe(this@OverlayService, file, cap)
        }
        val mode = PrefsBridge.getMode(this@OverlayService)
        val claudeKey = PrefsBridge.getClaudeApiKey(this@OverlayService)
        val finalText = if (claudeKey.isBlank() || raw.isBlank()) raw
        else runCatching {
          ModeProcessor.process(raw, mode, claudeKey)
        }.getOrElse {
          toast("Claude failed: ${it.message?.take(60)}")
          raw
        }
        injectOrCopy(finalText)
      } catch (t: Throwable) {
        toast("Transcription failed: ${t.message?.take(80)}")
      } finally {
        pill?.setState(OverlayPillView.State.IDLE)
        try { file.delete() } catch (_: Throwable) {}
      }
    }
  }

  private fun injectOrCopy(text: String) {
    if (text.isBlank()) {
      toast("Empty transcription")
      return
    }
    val injected = A11yBus.injectText(text)
    if (!injected) {
      val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      cm.setPrimaryClip(android.content.ClipData.newPlainText("VoiceBoard", text))
      toast("Copied (no focused field)")
    }
  }

  private fun showModeMenu() {
    if (modeMenu != null) { removeModeMenu(); return }
    val menu = ModeMenuView(this) { mode ->
      PrefsBridge.setMode(this, mode)
      toast("Mode: $mode")
      removeModeMenu()
    }
    val p = pillParams
    val w = dp(180f).toInt(); val h = dp(140f).toInt()
    val params = WindowManager.LayoutParams(
        w,
        h,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = ((p?.x ?: 0) + ((pill?.width ?: 0) - w) / 2).coerceAtLeast(0)
      y = ((p?.y ?: 0) - h - dp(8f).toInt()).coerceAtLeast(0)
    }
    try {
      wm.addView(menu, params)
      modeMenu = menu
    } catch (_: Throwable) {}
  }

  private fun removeModeMenu() {
    modeMenu?.let { v -> try { wm.removeView(v) } catch (_: Throwable) {} }
    modeMenu = null
  }

  private fun stopOverlay() {
    transcribeJob?.cancel()
    try { capture?.stop() } catch (_: Throwable) {}
    removePill()
    removeModeMenu()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun dp(v: Float): Float =
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

  private fun toast(msg: String) {
    scope.launch { Toast.makeText(this@OverlayService, msg, Toast.LENGTH_SHORT).show() }
  }
}
