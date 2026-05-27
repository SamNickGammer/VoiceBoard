package com.samnick.voiceboard.ime

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Single store both the IME service and the RN-side native module read from.
 *
 * Non-secret prefs live in a plain SharedPreferences file; the API key lives in
 * an EncryptedSharedPreferences file backed by AndroidKeyStore.
 */
object PrefsBridge {
  const val PREFS_NAME = "voiceboard_prefs"
  const val SECURE_PREFS_NAME = "voiceboard_secure_prefs"

  const val KEY_ENGINE = "engine" // "claude" | "local"
  const val KEY_MODE = "mode" // "default" | "formal" | "generate"
  const val KEY_API_KEY = "claude_api_key"

  const val DEFAULT_ENGINE = "claude"
  const val DEFAULT_MODE = "default"

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

  fun getEngine(context: Context): String =
      prefs(context).getString(KEY_ENGINE, DEFAULT_ENGINE) ?: DEFAULT_ENGINE

  fun setEngine(context: Context, value: String) {
    prefs(context).edit().putString(KEY_ENGINE, value).apply()
  }

  fun getMode(context: Context): String =
      prefs(context).getString(KEY_MODE, DEFAULT_MODE) ?: DEFAULT_MODE

  fun setMode(context: Context, value: String) {
    prefs(context).edit().putString(KEY_MODE, value).apply()
  }

  fun getApiKey(context: Context): String =
      securePrefs(context).getString(KEY_API_KEY, "") ?: ""

  fun setApiKey(context: Context, value: String) {
    securePrefs(context).edit().putString(KEY_API_KEY, value).apply()
  }

  fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotBlank()
}
