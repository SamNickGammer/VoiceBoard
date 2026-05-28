package com.samnick.voiceboard.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Post-processes a Whisper transcript with a Groq LLM (free tier).
 * The same Groq key used for Whisper is reused here.
 */
object ModeProcessor {

  private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
  private const val MODEL = "llama-3.3-70b-versatile"
  private const val MAX_TOKENS = 400
  private val JSON = "application/json; charset=utf-8".toMediaType()

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
  }

  /**
   * @param mode "default" | "formal" | "generate"
   * @param keyboardLang "hi" (Hindi keyboard active → stay in Devanagari) or
   *                     anything else (English keyboard → Hindi speech becomes
   *                     Hinglish, English stays English).
   */
  suspend fun process(
      transcript: String,
      mode: String,
      keyboardLang: String,
      apiKey: String,
  ): String = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
      put("model", MODEL)
      put("max_tokens", MAX_TOKENS)
      put("temperature", 0.3)
      put("messages", JSONArray().apply {
        put(JSONObject().apply {
          put("role", "system")
          put("content", systemPrompt(mode, keyboardLang))
        })
        put(JSONObject().apply {
          put("role", "user")
          put("content", transcript)
        })
      })
    }

    val request = Request.Builder()
        .url(ENDPOINT)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("content-type", "application/json")
        .post(body.toString().toRequestBody(JSON))
        .build()

    client.newCall(request).execute().use { resp ->
      val responseBody = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) throw RuntimeException("Groq LLM ${resp.code}: $responseBody")
      JSONObject(responseBody)
          .optJSONArray("choices")
          ?.optJSONObject(0)
          ?.optJSONObject("message")
          ?.optString("content", "")
          ?.trim()
          .orEmpty()
    }
  }

  private fun systemPrompt(mode: String, keyboardLang: String): String {
    val outputLanguageRule = if (keyboardLang == "hi") {
      "Output script: Devanagari (Hindi). The user has a Hindi keyboard selected — keep all output in Devanagari Hindi. Do not romanise."
    } else {
      "Output script: Latin (English/Hinglish). The user has an English keyboard selected. If the speech was Hindi, output it as Hinglish — Hindi written with Latin letters (e.g. \"kya kar raha hai\"). If the speech was English, output English. Code-switching between Hindi and English in Latin script is natural and fine."
    }

    return when (mode) {
      "formal" -> """You are a professional writing assistant. The user spoke casually. Convert their speech into polished, formal written text.
$outputLanguageRule
Return ONLY the final rewritten text. No explanation, no preamble, no quotes."""

      "generate" -> """You are a message writer. The user described what they want to say. Write the actual message for them. Keep it concise and natural.
$outputLanguageRule
Return ONLY the final message text. No explanation, no preamble, no quotes."""

      else /* default */ -> """You are a transcription cleaner. The user has spoken text that has been transcribed. Fix obvious transcription errors and produce a clean version that preserves what they meant.
$outputLanguageRule
Return ONLY the final cleaned text. No explanation, no quotes."""
    }
  }
}
