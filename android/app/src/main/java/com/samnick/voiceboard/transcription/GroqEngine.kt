package com.samnick.voiceboard.transcription

import android.content.Context
import com.samnick.voiceboard.core.PrefsBridge
import com.samnick.voiceboard.overlay.AudioCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Groq's OpenAI-compatible Whisper endpoint. Free tier as of writing:
 * https://console.groq.com/docs/models#whisper-large-v3-turbo
 */
class GroqEngine : TranscriptionEngine {

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
  }

  override suspend fun transcribe(
      context: Context,
      wavFile: File,
      capture: AudioCapture?,
  ): String = withContext(Dispatchers.IO) {
    val apiKey = PrefsBridge.getGroqApiKey(context)
    require(apiKey.isNotBlank()) { "Groq API key not set" }

    val multipart = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            wavFile.name,
            wavFile.asRequestBody("audio/wav".toMediaType()),
        )
        .addFormDataPart("model", "whisper-large-v3-turbo")
        .addFormDataPart("response_format", "json")
        .addFormDataPart("temperature", "0")
        .build()

    val request = Request.Builder()
        .url("https://api.groq.com/openai/v1/audio/transcriptions")
        .addHeader("Authorization", "Bearer $apiKey")
        .post(multipart)
        .build()

    client.newCall(request).execute().use { resp ->
      val body = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) throw RuntimeException("Groq ${resp.code}: ${body.take(300)}")
      val json = JSONObject(body)
      json.optString("text", "").trim()
    }
  }

  /** Sanity check — sends a 0.5 s silence wav. Returns the (possibly empty) string. */
  suspend fun ping(context: Context, apiKey: String): String = withContext(Dispatchers.IO) {
    val tmp = File.createTempFile("voiceboard-ping", ".wav", context.cacheDir)
    try {
      writeSilenceWav(tmp, durationSec = 0.5f)
      val multipart = MultipartBody.Builder()
          .setType(MultipartBody.FORM)
          .addFormDataPart("file", tmp.name, tmp.asRequestBody("audio/wav".toMediaType()))
          .addFormDataPart("model", "whisper-large-v3-turbo")
          .addFormDataPart("response_format", "json")
          .build()
      val request = Request.Builder()
          .url("https://api.groq.com/openai/v1/audio/transcriptions")
          .addHeader("Authorization", "Bearer $apiKey")
          .post(multipart)
          .build()
      client.newCall(request).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw RuntimeException("Groq ${resp.code}: ${body.take(200)}")
        JSONObject(body).optString("text", "")
      }
    } finally {
      try { tmp.delete() } catch (_: Throwable) {}
    }
  }

  private fun writeSilenceWav(file: File, durationSec: Float) {
    val sampleRate = 16_000
    val samples = (sampleRate * durationSec).toInt()
    val dataSize = samples * 2
    val out = file.outputStream()
    val buf = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray()); buf.putInt(36 + dataSize); buf.put("WAVE".toByteArray())
    buf.put("fmt ".toByteArray()); buf.putInt(16); buf.putShort(1); buf.putShort(1)
    buf.putInt(sampleRate); buf.putInt(sampleRate * 2); buf.putShort(2); buf.putShort(16)
    buf.put("data".toByteArray()); buf.putInt(dataSize)
    out.write(buf.array())
    out.write(ByteArray(dataSize))
    out.close()
  }
}
