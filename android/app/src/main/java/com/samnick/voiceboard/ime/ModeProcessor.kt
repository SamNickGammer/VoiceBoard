package com.samnick.voiceboard.ime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ModeProcessor {

  private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
  private const val MODEL = "claude-sonnet-4-20250514"
  private const val MAX_TOKENS = 300
  private const val API_VERSION = "2023-06-01"

  private val JSON = "application/json; charset=utf-8".toMediaType()

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
  }

  // Prompts intentionally mirror src/utils/claudeApi.ts.
  private val PROMPTS = mapOf(
      "default" to "You are a transcription cleaner. The user has spoken text that has been transcribed. Return only the cleaned transcription — fix obvious transcription errors, keep the original language (Hindi, English, or Hinglish mix). Return ONLY the final text, nothing else. No explanation, no quotes.",
      "formal" to "You are a professional writing assistant. The user spoke casually. Convert their speech into polished, formal written English. Return ONLY the final text, nothing else. No explanation, no preamble, no quotes.",
      "generate" to "You are a message writer. The user described what they want to say. Write the actual message for them. Keep it concise and natural. Return ONLY the final text, nothing else. No explanation, no preamble, no quotes.",
  )

  /**
   * Sends [transcript] to Claude with the system prompt for [mode]. Returns the
   * cleaned text, or throws on network / API errors. Should be called from a
   * background coroutine.
   */
  suspend fun process(transcript: String, mode: String, apiKey: String): String =
      withContext(Dispatchers.IO) {
        val system = PROMPTS[mode] ?: PROMPTS.getValue("default")

        val body = JSONObject().apply {
          put("model", MODEL)
          put("max_tokens", MAX_TOKENS)
          put("system", system)
          put("messages", JSONArray().apply {
            put(JSONObject().apply {
              put("role", "user")
              put("content", transcript)
            })
          })
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
          val responseBody = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            throw RuntimeException("Claude API ${resp.code}: $responseBody")
          }
          extractText(responseBody)
        }
      }

  private fun extractText(json: String): String {
    val root = JSONObject(json)
    val content = root.optJSONArray("content") ?: return ""
    val sb = StringBuilder()
    for (i in 0 until content.length()) {
      val block = content.optJSONObject(i) ?: continue
      if (block.optString("type") == "text") {
        sb.append(block.optString("text"))
      }
    }
    return sb.toString().trim()
  }
}
