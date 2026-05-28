package com.samnick.voiceboard.bridge

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class VoiceBoardPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
      if (name == VoiceBoardModule.NAME) VoiceBoardModule(reactContext) else null

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
    mapOf(
        VoiceBoardModule.NAME to ReactModuleInfo(
            VoiceBoardModule.NAME,
            VoiceBoardModule::class.java.name,
            /* canOverrideExistingModule = */ false,
            /* needsEagerInit = */ false,
            /* isCxxModule = */ false,
            // We're a plain ReactContextBaseJavaModule (no codegen'd TurboModule spec).
            // Marked legacy so the bridgeless interop layer wraps it; combined with
            // useTurboModuleInterop=true (set via Canary release level) this is what
            // makes NativeModules.VoiceBoard resolve on the JS side.
            /* isTurboModule = */ false,
        ),
    )
  }
}
