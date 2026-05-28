package com.samnick.voiceboard.transcription

import android.content.Context
import com.samnick.voiceboard.overlay.AudioCapture
import java.io.File

/**
 * Implemented by each transcription backend. Engines are stateless; per-call
 * configuration (file or PCM, language hint, model path, etc.) is passed in.
 *
 * @param languageHint either "hi" (force Hindi decoding) or "en" / null
 *                     (let Whisper auto-detect). The router derives this from
 *                     the user's currently-selected on-screen keyboard.
 */
interface TranscriptionEngine {
  suspend fun transcribe(
      context: Context,
      wavFile: File,
      capture: AudioCapture?,
      languageHint: String?,
  ): String
}
