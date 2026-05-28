package com.samnick.voiceboard

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.samnick.voiceboard.bridge.VoiceBoardPackage

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(VoiceBoardPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    // CANARY enables `useTurboModuleInterop`, which is what lets legacy
    // ReactContextBaseJavaModule modules (like VoiceBoardModule, which has
    // no codegen'd TurboModule spec) resolve through TurboModuleRegistry on
    // the JS side. STABLE leaves this off and our module returns null.
    DefaultNewArchitectureEntryPoint.releaseLevel = ReleaseLevel.CANARY
    loadReactNative(this)
  }
}
