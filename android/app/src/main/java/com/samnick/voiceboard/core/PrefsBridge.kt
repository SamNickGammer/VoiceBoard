package com.samnick.voiceboard.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Single store the overlay service, accessibility service, and the
 * RN-side native module all read from.
 *
 * Non-secret prefs live in a plain SharedPreferences file; API keys
 * live in an EncryptedSharedPreferences file backed by AndroidKeyStore.
 */
object PrefsBridge {
  const val PREFS_NAME = "voiceboard_prefs"
  const val SECURE_PREFS_NAME = "voiceboard_secure_prefs"

  // Transcription engine — "groq" | "local"
  const val KEY_TRANSCRIPTION_ENGINE = "transcription_engine"
  const val DEFAULT_TRANSCRIPTION_ENGINE = "groq"

  // Post-processing mode — "default" | "formal" | "generate"
  const val KEY_MODE = "mode"
  const val DEFAULT_MODE = "default"

  // Filename (no path) of the currently-selected local whisper model in filesDir/whisper-models/.
  const val KEY_ACTIVE_MODEL = "active_model"

  // Persisted overlay pill position.
  const val KEY_OVERLAY_X = "overlay_x"
  const val KEY_OVERLAY_Y = "overlay_y"

  // Whether the overlay is "armed" — i.e. the user has asked us to keep it running.
  const val KEY_OVERLAY_ENABLED = "overlay_enabled"

  // Secure keys
  const val KEY_GROQ_API_KEY = "groq_api_key"

  fun prefs(context: Context): SharedPreferences =
      context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun securePrefs(context: Context): SharedPreferences {
    val app = context.applicationContext
    val masterKey =
        MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    return EncryptedSharedPreferences.create(
        app,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  fun getTranscriptionEngine(context: Context): String =
      prefs(context).getString(KEY_TRANSCRIPTION_ENGINE, DEFAULT_TRANSCRIPTION_ENGINE)
          ?: DEFAULT_TRANSCRIPTION_ENGINE

  fun setTranscriptionEngine(context: Context, value: String) {
    prefs(context).edit().putString(KEY_TRANSCRIPTION_ENGINE, value).apply()
  }

  fun getMode(context: Context): String =
      prefs(context).getString(KEY_MODE, DEFAULT_MODE) ?: DEFAULT_MODE

  fun setMode(context: Context, value: String) {
    prefs(context).edit().putString(KEY_MODE, value).apply()
  }

  fun getActiveModel(context: Context): String? = prefs(context).getString(KEY_ACTIVE_MODEL, null)

  fun setActiveModel(context: Context, value: String?) {
    prefs(context).edit().apply {
      if (value == null) remove(KEY_ACTIVE_MODEL) else putString(KEY_ACTIVE_MODEL, value)
      apply()
    }
  }

  fun getOverlayPosition(context: Context): Pair<Int, Int> {
    val p = prefs(context)
    return Pair(p.getInt(KEY_OVERLAY_X, Int.MIN_VALUE), p.getInt(KEY_OVERLAY_Y, Int.MIN_VALUE))
  }

  fun setOverlayPosition(context: Context, x: Int, y: Int) {
    prefs(context).edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
  }

  fun isOverlayEnabled(context: Context): Boolean =
      prefs(context).getBoolean(KEY_OVERLAY_ENABLED, false)

  fun setOverlayEnabled(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()
  }

  fun getGroqApiKey(context: Context): String =
      securePrefs(context).getString(KEY_GROQ_API_KEY, "") ?: ""

  fun setGroqApiKey(context: Context, value: String) {
    securePrefs(context).edit().putString(KEY_GROQ_API_KEY, value).apply()
  }

  fun hasGroqApiKey(context: Context): Boolean = getGroqApiKey(context).isNotBlank()
}
