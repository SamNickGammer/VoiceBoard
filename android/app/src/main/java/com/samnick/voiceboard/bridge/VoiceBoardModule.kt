package com.samnick.voiceboard.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.samnick.voiceboard.MainActivity
import com.samnick.voiceboard.ime.PrefsBridge

class VoiceBoardModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "VoiceBoard"

  @ReactMethod
  fun getEngine(promise: Promise) {
    promise.resolve(PrefsBridge.getEngine(reactContext))
  }

  @ReactMethod
  fun setEngine(value: String, promise: Promise) {
    PrefsBridge.setEngine(reactContext, value)
    promise.resolve(null)
  }

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
  fun setApiKey(value: String, promise: Promise) {
    try {
      PrefsBridge.setApiKey(reactContext, value)
      promise.resolve(null)
    } catch (t: Throwable) {
      promise.reject("E_SET_API_KEY", t)
    }
  }

  @ReactMethod
  fun hasApiKey(promise: Promise) {
    promise.resolve(PrefsBridge.hasApiKey(reactContext))
  }

  @ReactMethod
  fun isKeyboardEnabled(promise: Promise) {
    val imm = reactContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val id = "${reactContext.packageName}/com.samnick.voiceboard.ime.VoiceBoardIME"
    val enabled = imm.enabledInputMethodList.any { it.id == id }
    promise.resolve(enabled)
  }

  @ReactMethod
  fun hasMicPermission(promise: Promise) {
    if (Build.VERSION.SDK_INT < 23) {
      promise.resolve(true)
      return
    }
    val granted = reactContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    promise.resolve(granted)
  }

  @ReactMethod
  fun requestMicPermission(promise: Promise) {
    val activity = reactContext.currentActivity as? MainActivity
    if (activity == null) {
      promise.reject("E_NO_ACTIVITY", "No current activity to request permission from")
      return
    }
    activity.requestRecordAudio { granted -> promise.resolve(granted) }
  }

  @ReactMethod
  fun openImeSettings() {
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    reactContext.startActivity(intent)
  }

  @ReactMethod
  fun showImePicker() {
    val imm = reactContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showInputMethodPicker()
  }
}
