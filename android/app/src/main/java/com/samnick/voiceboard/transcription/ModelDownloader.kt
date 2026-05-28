package com.samnick.voiceboard.transcription

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.samnick.voiceboard.core.PrefsBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Streaming downloader for whisper.cpp GGML models. Catalog lives in [CATALOG].
 * Progress + status lines emitted to RN via `whisper-download` DeviceEventEmitter.
 */
object ModelDownloader {

  data class ModelSpec(val name: String, val label: String, val url: String, val sizeMb: Int)

  /** Curated list of GGML models. URLs are the canonical Hugging Face hosts. */
  val CATALOG: List<ModelSpec> = listOf(
      ModelSpec(
          name = "ggml-tiny.bin",
          label = "tiny (multilingual, ~75 MB) — fastest, lower quality",
          url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
          sizeMb = 75,
      ),
      ModelSpec(
          name = "ggml-base.bin",
          label = "base (multilingual, ~142 MB) — recommended default",
          url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
          sizeMb = 142,
      ),
      ModelSpec(
          name = "ggml-small.bin",
          label = "small (multilingual, ~466 MB) — best quality, slower",
          url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
          sizeMb = 466,
      ),
      ModelSpec(
          name = "ggml-tiny.en.bin",
          label = "tiny.en (English only, ~75 MB)",
          url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
          sizeMb = 75,
      ),
      ModelSpec(
          name = "ggml-base.en.bin",
          label = "base.en (English only, ~142 MB)",
          url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
          sizeMb = 142,
      ),
  )

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // long-running download
        .build()
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val activeJobs = mutableMapOf<String, Job>()
  private val jobsLock = Any()

  fun modelsDir(context: Context): File =
      File(context.filesDir, "whisper-models").also { it.mkdirs() }

  fun modelFile(context: Context, name: String): File = File(modelsDir(context), name)

  fun activeModelFile(context: Context): File? {
    val active = PrefsBridge.getActiveModel(context) ?: return null
    val f = modelFile(context, active)
    return if (f.exists() && f.length() > 0) f else null
  }

  fun installedModels(context: Context): List<String> {
    val dir = modelsDir(context)
    if (!dir.exists()) return emptyList()
    return dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()
  }

  fun cancel(name: String) {
    synchronized(jobsLock) { activeJobs.remove(name)?.cancel() }
  }

  fun startDownload(context: ReactApplicationContext, name: String) {
    val spec = CATALOG.firstOrNull { it.name == name }
        ?: run { emit(context, name, "error", "Unknown model: $name"); return }
    val dest = modelFile(context, name)
    val tmp = File(dest.parentFile, "${dest.name}.part")

    synchronized(jobsLock) {
      if (activeJobs[name]?.isActive == true) {
        emit(context, name, "info", "Already downloading")
        return
      }
    }

    val job = scope.launch {
      try {
        emit(context, name, "step", "Preparing download")
        if (dest.exists()) {
          emit(context, name, "info", "Already installed at ${dest.absolutePath}")
          emit(context, name, "done", "Installed", bytes = dest.length(), total = dest.length())
          return@launch
        }
        emit(context, name, "step", "Fetching ${spec.url}")
        val req = Request.Builder().url(spec.url).get().build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            emit(context, name, "error", "HTTP ${resp.code}")
            return@launch
          }
          val total = resp.body?.contentLength() ?: -1L
          emit(context, name, "info",
              if (total > 0) "Size: ${total / 1_048_576} MB"
              else "Size: unknown — streaming")
          val source = resp.body?.byteStream() ?: run {
            emit(context, name, "error", "Empty body")
            return@launch
          }
          withContext(Dispatchers.IO) {
            tmp.outputStream().use { out ->
              val buf = ByteArray(64 * 1024)
              var copied = 0L
              var lastEmit = 0L
              while (true) {
                val n = source.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                copied += n
                if (copied - lastEmit > 1_048_576) { // every 1 MB
                  lastEmit = copied
                  emit(context, name, "progress", "${copied / 1_048_576} MB",
                      bytes = copied, total = total)
                }
              }
            }
          }
          if (!tmp.renameTo(dest)) {
            emit(context, name, "error", "Could not finalise ${dest.name}")
            try { tmp.delete() } catch (_: Throwable) {}
            return@launch
          }
          val bytes = dest.length()
          emit(context, name, "done", "Downloaded ${bytes / 1_048_576} MB",
              bytes = bytes, total = bytes)
        }
      } catch (t: Throwable) {
        emit(context, name, "error", "Failed: ${t.message?.take(160)}")
        try { tmp.delete() } catch (_: Throwable) {}
      } finally {
        synchronized(jobsLock) { activeJobs.remove(name) }
      }
    }
    synchronized(jobsLock) { activeJobs[name] = job }
  }

  fun deleteModel(context: Context, name: String): Boolean {
    val f = modelFile(context, name)
    val ok = f.exists() && f.delete()
    if (PrefsBridge.getActiveModel(context) == name) {
      PrefsBridge.setActiveModel(context, null)
      LocalWhisperEngine.unload()
    }
    return ok
  }

  private fun emit(
      context: Context,
      name: String,
      status: String,
      message: String,
      bytes: Long = -1L,
      total: Long = -1L,
  ) {
    val map = Arguments.createMap().apply {
      putString("name", name)
      putString("status", status)
      putString("message", message)
      if (bytes >= 0) putDouble("bytes", bytes.toDouble())
      if (total >= 0) putDouble("total", total.toDouble())
    }
    val rc = context as? ReactApplicationContext ?: return
    try {
      rc.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          ?.emit("whisper-download", map)
    } catch (_: Throwable) {}
  }

  fun shutdown() { scope.cancel() }
}
