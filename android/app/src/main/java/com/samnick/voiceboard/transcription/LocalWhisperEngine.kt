package com.samnick.voiceboard.transcription

import android.content.Context
import com.samnick.voiceboard.core.PrefsBridge
import com.samnick.voiceboard.overlay.AudioCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device Whisper via whisper.cpp linked through [WhisperJNI].
 * Single context is reused; we re-init only when the active model changes.
 */
object LocalWhisperEngine : TranscriptionEngine {

  @Volatile private var loadedModelPath: String? = null
  private val initLock = Any()

  override suspend fun transcribe(
      context: Context,
      wavFile: File,
      capture: AudioCapture?,
  ): String = withContext(Dispatchers.Default) {
    val modelFile = ModelDownloader.activeModelFile(context)
        ?: throw IllegalStateException("No local whisper model selected. Download one in Settings.")
    ensureLoaded(modelFile.absolutePath)
    val pcm = capture?.readAsFloats() ?: readWavAsFloats(wavFile)
    if (pcm.isEmpty()) return@withContext ""
    val nThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
    val out = WhisperJNI.transcribe(pcm, nThreads)
    out.trim()
  }

  private fun ensureLoaded(modelPath: String) {
    if (loadedModelPath == modelPath) return
    synchronized(initLock) {
      if (loadedModelPath == modelPath) return
      if (loadedModelPath != null) {
        try { WhisperJNI.release() } catch (_: Throwable) {}
        loadedModelPath = null
      }
      val ok = WhisperJNI.init(modelPath)
      if (!ok) throw RuntimeException("whisper.cpp failed to load $modelPath")
      loadedModelPath = modelPath
    }
  }

  private fun readWavAsFloats(file: File): FloatArray {
    val raw = file.readBytes()
    if (raw.size <= 44) return FloatArray(0)
    val pcmBytes = raw.copyOfRange(44, raw.size)
    val out = FloatArray(pcmBytes.size / 2)
    var i = 0; var j = 0
    while (i + 1 < pcmBytes.size) {
      val sample = (pcmBytes[i].toInt() and 0xFF) or (pcmBytes[i + 1].toInt() shl 8)
      val signed = sample.toShort().toInt()
      out[j++] = signed / 32768f
      i += 2
    }
    return out
  }

  fun unload() {
    synchronized(initLock) {
      if (loadedModelPath != null) {
        try { WhisperJNI.release() } catch (_: Throwable) {}
        loadedModelPath = null
      }
    }
  }
}

/**
 * JNI bridge to libvoiceboardwhisper.so (whisper.cpp + small wrapper).
 *
 * The native library is loaded lazily on first access. Loading whisper.cpp at
 * app startup conflicts with the RN runtime (TurboModule registry initialisation
 * fails before any JS evaluates), so callers must explicitly request the lib
 * via [ensureLoaded] / [libAvailable].
 */
internal object WhisperJNI {
  @Volatile private var loadAttempted = false
  @Volatile private var loaded = false
  @Volatile var loadError: String? = null
    private set
  private val loadLock = Any()

  val libAvailable: Boolean
    get() {
      ensureLoaded()
      return loaded
    }

  fun ensureLoaded() {
    if (loadAttempted) return
    synchronized(loadLock) {
      if (loadAttempted) return
      loadAttempted = true
      try {
        System.loadLibrary("voiceboardwhisper")
        loaded = true
      } catch (t: Throwable) {
        loaded = false
        loadError = t.message ?: t.javaClass.simpleName
      }
    }
  }

  fun init(modelPath: String): Boolean {
    ensureLoaded()
    if (!loaded) throw RuntimeException(
        "Native whisper library not available: ${loadError ?: "unknown error"}",
    )
    return nativeInit(modelPath)
  }

  fun transcribe(pcm: FloatArray, nThreads: Int): String {
    ensureLoaded()
    if (!loaded) throw RuntimeException("Native whisper library not available")
    return nativeTranscribe(pcm, nThreads) ?: ""
  }

  fun release() {
    if (!loaded) return
    nativeRelease()
  }

  private external fun nativeInit(modelPath: String): Boolean
  private external fun nativeTranscribe(pcm: FloatArray, nThreads: Int): String?
  private external fun nativeRelease()
}

object PrefsBridgeProxy {
  // tiny indirection so this file doesn't bring PrefsBridge into its direct import chain
  // (kept here in case we move LocalWhisperEngine into a feature module later).
  fun activeModel(context: Context): String? = PrefsBridge.getActiveModel(context)
}
