package com.samnick.voiceboard.transcription

import android.content.Context
import com.samnick.voiceboard.core.PrefsBridge
import com.samnick.voiceboard.overlay.AudioCapture
import java.io.File

object TranscriptionRouter {

  private val groq = GroqEngine()

  suspend fun transcribe(
      context: Context,
      wavFile: File,
      capture: AudioCapture?,
      languageHint: String?,
  ): String {
    val engine = PrefsBridge.getTranscriptionEngine(context)
    return engineFor(engine).transcribe(context, wavFile, capture, languageHint)
  }

  fun engineFor(name: String): TranscriptionEngine = when (name) {
    "local" -> LocalWhisperEngine
    else -> groq
  }

  fun groq(): GroqEngine = groq
}
