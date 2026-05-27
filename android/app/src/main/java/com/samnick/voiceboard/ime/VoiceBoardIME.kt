package com.samnick.voiceboard.ime

import android.content.Context
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceBoardIME : InputMethodService() {

  private var keyboardView: VoiceBoardKeyboardView? = null
  private var recorder: MediaRecorder? = null
  private var currentRecordingPath: String? = null
  private var isRecording = false
  private var pendingJob: Job? = null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onCreateInputView(): View {
    val view = VoiceBoardKeyboardView(this).also { it.listener = listenerImpl }
    view.setMode(PrefsBridge.getMode(this))
    keyboardView = view
    return view
  }

  override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    keyboardView?.setMode(PrefsBridge.getMode(this))
    keyboardView?.setStatus("")
  }

  override fun onDestroy() {
    super.onDestroy()
    safeStopRecorder()
    pendingJob?.cancel()
    scope.cancel()
  }

  private val listenerImpl = object : VoiceBoardKeyboardView.Listener {
    override fun onKey(char: Char) {
      currentInputConnection?.commitText(char.toString(), 1)
    }

    override fun onBackspace() {
      val ic = currentInputConnection ?: return
      val selected = ic.getSelectedText(0)
      if (!selected.isNullOrEmpty()) {
        ic.commitText("", 1)
      } else {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
      }
    }

    override fun onEnter() {
      val ic = currentInputConnection ?: return
      ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
      ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onSpace() {
      currentInputConnection?.commitText(" ", 1)
    }

    override fun onMicTap() {
      if (isRecording) stopRecordingAndProcess() else startRecording()
    }

    override fun onSwitchIme() {
      val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.showInputMethodPicker()
    }

    override fun onModeSelected(mode: String) {
      PrefsBridge.setMode(this@VoiceBoardIME, mode)
    }
  }

  private fun startRecording() {
    val hasMic = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasMic) {
      keyboardView?.setStatus("Allow mic in VoiceBoard app first")
      return
    }

    val outFile = File(cacheDir, "voiceboard-${System.currentTimeMillis()}.m4a")
    currentRecordingPath = outFile.absolutePath

    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      MediaRecorder(this)
    } else {
      @Suppress("DEPRECATION") MediaRecorder()
    }
    try {
      rec.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(96000)
        setAudioSamplingRate(44100)
        setOutputFile(outFile.absolutePath)
        prepare()
        start()
      }
      recorder = rec
      isRecording = true
      keyboardView?.setRecording(true)
    } catch (t: Throwable) {
      try { rec.release() } catch (_: Throwable) {}
      recorder = null
      isRecording = false
      keyboardView?.setRecording(false)
      keyboardView?.setStatus("Recording failed: ${t.message}")
    }
  }

  private fun stopRecordingAndProcess() {
    val path = currentRecordingPath ?: return
    val stopped = safeStopRecorder()
    isRecording = false
    keyboardView?.setRecording(false)
    if (!stopped) {
      keyboardView?.setStatus("Couldn't stop recorder")
      return
    }

    keyboardView?.setStatus("Thinking...")
    pendingJob?.cancel()
    pendingJob = scope.launch {
      try {
        val raw = withContext(Dispatchers.IO) { TranscriptionManager.transcribe(path) }
        val mode = PrefsBridge.getMode(this@VoiceBoardIME)
        val apiKey = PrefsBridge.getApiKey(this@VoiceBoardIME)
        val output = if (apiKey.isBlank()) {
          raw
        } else {
          try {
            ModeProcessor.process(raw, mode, apiKey)
          } catch (t: Throwable) {
            keyboardView?.setStatus("Claude error: ${t.message?.take(40)}")
            raw
          }
        }
        currentInputConnection?.commitText(output, 1)
        if (apiKey.isBlank()) {
          keyboardView?.setStatus("No API key set — used raw text")
        } else {
          keyboardView?.setStatus("")
        }
      } finally {
        try { File(path).delete() } catch (_: Throwable) {}
      }
    }
  }

  private fun safeStopRecorder(): Boolean {
    val rec = recorder ?: return true
    return try {
      try { rec.stop() } catch (_: Throwable) { /* possible if no audio */ }
      rec.release()
      recorder = null
      true
    } catch (_: Throwable) {
      recorder = null
      false
    }
  }
}
