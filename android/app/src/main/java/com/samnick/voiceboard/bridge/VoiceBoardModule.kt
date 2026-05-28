package com.samnick.voiceboard.bridge

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.module.annotations.ReactModule
import com.samnick.voiceboard.MainActivity
import com.samnick.voiceboard.a11y.VoiceBoardAccessibilityService
import com.samnick.voiceboard.core.PrefsBridge
import com.samnick.voiceboard.overlay.OverlayService
import com.samnick.voiceboard.transcription.LocalWhisperEngine
import com.samnick.voiceboard.transcription.ModelDownloader
import com.samnick.voiceboard.transcription.TranscriptionRouter
import com.samnick.voiceboard.transcription.WhisperJNI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@ReactModule(name = VoiceBoardModule.NAME)
class VoiceBoardModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun getName(): String = NAME

  companion object {
    const val NAME = "VoiceBoard"
  }

  // ---- Mode / Claude ----

  @ReactMethod
  fun getMode(promise: Promise) {
    promise.resolve(PrefsBridge.getMode(reactContext))
  }

  @ReactMethod
  fun setMode(value: String, promise: Promise) {
    PrefsBridge.setMode(reactContext, value)
    promise.resolve(null)
  }

  @ReactMethod
  fun setClaudeApiKey(value: String, promise: Promise) {
    PrefsBridge.setClaudeApiKey(reactContext, value)
    promise.resolve(null)
  }

  @ReactMethod
  fun hasClaudeApiKey(promise: Promise) {
    promise.resolve(PrefsBridge.hasClaudeApiKey(reactContext))
  }

  // ---- Transcription engine + Groq ----

  @ReactMethod
  fun getTranscriptionEngine(promise: Promise) {
    promise.resolve(PrefsBridge.getTranscriptionEngine(reactContext))
  }

  @ReactMethod
  fun setTranscriptionEngine(value: String, promise: Promise) {
    PrefsBridge.setTranscriptionEngine(reactContext, value)
    promise.resolve(null)
  }

  @ReactMethod
  fun setGroqApiKey(value: String, promise: Promise) {
    PrefsBridge.setGroqApiKey(reactContext, value)
    promise.resolve(null)
  }

  @ReactMethod
  fun hasGroqApiKey(promise: Promise) {
    promise.resolve(PrefsBridge.hasGroqApiKey(reactContext))
  }

  @ReactMethod
  fun testGroqKey(apiKey: String, promise: Promise) {
    scope.launch {
      try {
        val text = TranscriptionRouter.groq().ping(reactContext, apiKey)
        promise.resolve(text)
      } catch (t: Throwable) {
        promise.reject("E_GROQ_TEST", t)
      }
    }
  }

  // ---- Permissions ----

  @ReactMethod
  fun hasMicPermission(promise: Promise) {
    if (Build.VERSION.SDK_INT < 23) { promise.resolve(true); return }
    val granted = reactContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    promise.resolve(granted)
  }

  @ReactMethod
  fun requestMicPermission(promise: Promise) {
    val activity = reactContext.currentActivity as? MainActivity
    if (activity == null) { promise.reject("E_NO_ACTIVITY", "No current activity"); return }
    activity.requestRecordAudio { granted -> promise.resolve(granted) }
  }

  @ReactMethod
  fun hasOverlayPermission(promise: Promise) {
    promise.resolve(Settings.canDrawOverlays(reactContext))
  }

  @ReactMethod
  fun requestOverlayPermission() {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${reactContext.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    reactContext.startActivity(intent)
  }

  @ReactMethod
  fun isAccessibilityEnabled(promise: Promise) {
    promise.resolve(VoiceBoardAccessibilityService.isEnabled(reactContext))
  }

  @ReactMethod
  fun openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    reactContext.startActivity(intent)
  }

  // ---- Overlay ----

  @ReactMethod
  fun startOverlay(promise: Promise) {
    if (!Settings.canDrawOverlays(reactContext)) {
      promise.reject("E_NO_OVERLAY_PERMISSION", "Overlay permission not granted")
      return
    }
    OverlayService.start(reactContext)
    promise.resolve(true)
  }

  @ReactMethod
  fun stopOverlay(promise: Promise) {
    OverlayService.stop(reactContext)
    promise.resolve(true)
  }

  @ReactMethod
  fun isOverlayRunning(promise: Promise) {
    promise.resolve(OverlayService.running)
  }

  // ---- Local models ----

  @ReactMethod
  fun listModelCatalog(promise: Promise) {
    val arr: WritableArray = Arguments.createArray()
    val installed = ModelDownloader.installedModels(reactContext).toSet()
    val active = PrefsBridge.getActiveModel(reactContext)
    ModelDownloader.CATALOG.forEach { spec ->
      val m = Arguments.createMap()
      m.putString("name", spec.name)
      m.putString("label", spec.label)
      m.putString("url", spec.url)
      m.putInt("sizeMb", spec.sizeMb)
      m.putBoolean("installed", installed.contains(spec.name))
      m.putBoolean("active", spec.name == active)
      arr.pushMap(m)
    }
    promise.resolve(arr)
  }

  @ReactMethod
  fun downloadModel(name: String) {
    ModelDownloader.startDownload(reactContext, name)
  }

  @ReactMethod
  fun cancelDownload(name: String) {
    ModelDownloader.cancel(name)
  }

  @ReactMethod
  fun deleteModel(name: String, promise: Promise) {
    val ok = ModelDownloader.deleteModel(reactContext, name)
    promise.resolve(ok)
  }

  @ReactMethod
  fun setActiveModel(name: String?, promise: Promise) {
    PrefsBridge.setActiveModel(reactContext, if (name.isNullOrBlank()) null else name)
    LocalWhisperEngine.unload()
    promise.resolve(null)
  }

  @ReactMethod
  fun getActiveModel(promise: Promise) {
    promise.resolve(PrefsBridge.getActiveModel(reactContext))
  }

  @ReactMethod
  fun isLocalWhisperLibAvailable(promise: Promise) {
    promise.resolve(WhisperJNI.libAvailable)
  }

  // ---- DeviceEventEmitter listener bookkeeping (RN convention) ----

  @ReactMethod
  fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) {}

  @ReactMethod
  fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Int) {}
}
