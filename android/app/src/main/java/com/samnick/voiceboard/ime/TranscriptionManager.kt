package com.samnick.voiceboard.ime

/**
 * Phase 1 stub. Returns a placeholder string so the mic -> mode-processor ->
 * commit-text pipeline can be verified end-to-end without a real STT model.
 *
 * Phase 2 will swap this for an on-device Whisper invocation (or a Claude
 * multimodal call if we go that direction), reading the audio file at [path].
 */
object TranscriptionManager {
  const val PHASE_1_PLACEHOLDER = "[audio recorded - transcription coming in phase 2]"

  suspend fun transcribe(@Suppress("UNUSED_PARAMETER") path: String): String =
      PHASE_1_PLACEHOLDER
}
