package com.samnick.voiceboard

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    // Must be called before super.onCreate; swaps the splash theme out for AppTheme
    // and back-ports the Android 12 splash API to older devices.
    installSplashScreen()
    super.onCreate(savedInstanceState)
  }

  override fun getMainComponentName(): String = "VoiceBoard"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  fun requestRecordAudio(callback: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < 23) {
      callback(true)
      return
    }
    val granted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
      callback(true)
      return
    }
    pendingMicCallback = callback
    ActivityCompat.requestPermissions(
        this,
        arrayOf(android.Manifest.permission.RECORD_AUDIO),
        REQ_RECORD_AUDIO,
    )
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<String>,
      grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQ_RECORD_AUDIO) {
      val granted = grantResults.isNotEmpty() &&
          grantResults[0] == PackageManager.PERMISSION_GRANTED
      pendingMicCallback?.invoke(granted)
      pendingMicCallback = null
    }
  }

  companion object {
    private const val REQ_RECORD_AUDIO = 4317
    var pendingMicCallback: ((Boolean) -> Unit)? = null
  }
}
