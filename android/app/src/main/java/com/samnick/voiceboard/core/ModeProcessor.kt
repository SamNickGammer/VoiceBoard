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
 * Post-processes a Whisper transcript with a Groq LLM. The point isn't to
 * "clean it up" word-for-word — it's to figure out what the user actually
 * meant to send. People dictate the way they think: with self-corrections
 * ("4 no no 5"), filler ("um", "like"), and verbal backtracks ("scratch
 * that"). The LLM resolves all of that into the final intended message.
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
      // Low but non-zero — deterministic enough that "4 no no 5" reliably
      // becomes 5, with a tiny bit of slack for natural phrasing.
      put("temperature", 0.2)
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

  private fun systemPrompt(mode: String, keyboardLang: String): String =
      "${rolePrompt(mode)}\n\n${commonRules()}\n\n${languageRule(keyboardLang)}\n\n${examples(mode, keyboardLang)}"

  private fun rolePrompt(mode: String): String = when (mode) {
    "formal" -> """You are a writing assistant. The user dictated by voice — they thought out loud, self-corrected, used filler. Your job is to recover the final intent and rewrite it as a polished, professional message. Not stiff or corporate — just clean and competent."""
    "generate" -> """You are a message writer. The user described WHAT they want to send (a brief), not the literal words. Write the actual message they should send to the recipient. Make it natural, concise, and complete."""
    else -> """You are an intelligent dictation assistant — NOT a literal transcriber. The user dictates messages by voice and thinks out loud as they speak. Your job is to recover the final intended message — keeping their voice and register — and drop all the verbal noise."""
  }

  private fun commonRules(): String = """RULES (apply to every output):
1. SELF-CORRECTIONS: When the user changes their mind mid-sentence, the LATEST direction wins. The earlier version is discarded.
     "let's meet at 4 no no at 5" → "5"
     "send him 200 rupees wait 250" → "250"
     "kal milte hain 4 baje nahi nahi 5 baje" → "5 baje"
2. DROP FILLER: "um", "uh", "you know", "like", "I mean", "matlab", "yaar", "basically", "actually" — unless the word is load-bearing in context.
3. DROP VERBAL BACKTRACKS: "wait", "no no", "scratch that", "ruk ruk", "nahi nahi", "haan wait".
4. KEEP THE USER'S VOICE: don't expand short messages into long ones, don't add salutations the user didn't dictate, don't rephrase beyond what's needed to drop noise.
5. RETURN ONLY THE FINAL MESSAGE TEXT. No prefix, no quotes, no explanation, no markdown, no \"Here is\". Just the message."""

  private fun languageRule(keyboardLang: String): String = if (keyboardLang == "hi") {
    """OUTPUT SCRIPT: Devanagari (Hindi). The user has a Hindi keyboard selected. Keep all Hindi in Devanagari. Do NOT romanise. English words inside a Hindi sentence stay in Latin script as the user spoke them."""
  } else {
    """OUTPUT SCRIPT: Latin (English / Hinglish). The user has an English keyboard. If the input is in Devanagari, TRANSLITERATE it to Hinglish — Hindi written with Latin letters, the way someone texts on WhatsApp (e.g. "क्या कर रहा है" → "kya kar raha hai"). English stays English. Mixed Hindi-English in Latin script (code-switching) is natural and correct."""
  }

  private fun examples(mode: String, keyboardLang: String): String {
    val hi = keyboardLang == "hi"
    val ex = when (mode) {
      "formal" -> listOf(
          Example(
              input = "hey can you send the deck by tomorrow morning um actually by today evening",
              output = "Hi — could you share the deck by this evening?",
          ),
          Example(
              input = "tell rohit meeting moved to friday no wait thursday at 3",
              output = "Hi Rohit, the meeting has been moved to Thursday at 3.",
          ),
          if (hi) Example(
              input = "kal report bhej do nahi nahi parso tak chalega",
              output = "कृपया रिपोर्ट परसों तक भेज दें।",
          ) else Example(
              input = "kal report bhej do nahi nahi parso tak chalega",
              output = "Parso tak report bhej dena, koi jaldi nahi hai.",
          ),
      )
      "generate" -> listOf(
          Example(
              input = "tell mom i'll reach home late tonight maybe around 11",
              output = "Mom, reaching home around 11 tonight — don't wait up.",
          ),
          Example(
              input = "ask the team if friday 4 pm works for the review no no make it 5",
              output = "Does Friday 5 PM work for the review?",
          ),
          if (hi) Example(
              input = "papa ko bolo paise bhej diye check kar lein",
              output = "पापा, पैसे भेज दिए हैं — चेक कर लीजिए।",
          ) else Example(
              input = "papa ko bolo paise bhej diye check kar lein",
              output = "Papa, paise bhej diye hain, check kar lijiye.",
          ),
      )
      else /* default */ -> listOf(
          Example(
              input = "let's connect at 4 no no let's connect at 5",
              output = "Let's connect at 5.",
          ),
          Example(
              input = "send him 200 rupees wait make it 250",
              output = "Send him 250 rupees.",
          ),
          Example(
              input = "um I think we should uh like push the demo to friday actually thursday works",
              output = "I think we should push the demo to Thursday.",
          ),
          if (hi) Example(
              input = "kal milte hain 4 baje nahi nahi 5 baje",
              output = "कल मिलते हैं 5 बजे।",
          ) else Example(
              input = "kal milte hain 4 baje nahi nahi 5 baje",
              output = "Kal milte hain 5 baje.",
          ),
      )
    }
    val joined = ex.joinToString("\n\n") { "Input: ${it.input}\nOutput: ${it.output}" }
    return "EXAMPLES:\n$joined"
  }

  private data class Example(val input: String, val output: String)
}
