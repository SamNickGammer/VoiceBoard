package com.samnick.voiceboard.transcription

import android.content.Context
import com.samnick.voiceboard.overlay.AudioCapture
import java.io.File

/**
 * Implemented by each transcription backend. Engines are stateless; per-call
 * configuration (file or PCM, language hint, model path, etc.) is passed in.
 */
interface TranscriptionEngine {
  /**
   * Transcribe the audio in [wavFile] (16 kHz mono PCM_16 WAV) and return the text.
   * The [capture] reference is provided so the engine can call
   * [AudioCapture.readAsFloats] if it prefers raw PCM (local whisper does).
   *
   * Throws on transport or engine-level failures.
   */
  suspend fun transcribe(context: Context, wavFile: File, capture: AudioCapture?): String
}
