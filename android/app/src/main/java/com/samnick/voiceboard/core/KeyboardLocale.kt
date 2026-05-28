package com.samnick.voiceboard.core

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodManager

/**
 * What language is the user's currently-selected on-screen keyboard set to?
 * We use this to decide:
 *  - Whisper: whether to pass language="hi" to bias decoding toward Devanagari.
 *  - Mode LLM: whether to keep Hindi in Devanagari or romanise it to Hinglish.
 *
 * Returns "hi" for Hindi keyboards and "en" for everything else (English,
 * unknown, no subtype info). Falling back to "en" gives us the
 * Hinglish-friendly behaviour by default.
 */
object KeyboardLocale {
  fun detect(context: Context): String {
    val imm = context.applicationContext
        .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return "en"
    val subtype = try { imm.currentInputMethodSubtype } catch (_: Throwable) { null }
        ?: return "en"

    val tag = if (Build.VERSION.SDK_INT >= 24) subtype.languageTag.orEmpty() else ""
    if (tag.isNotEmpty()) {
      return if (tag.lowercase().startsWith("hi")) "hi" else "en"
    }

    // Older devices / IMEs that don't populate languageTag — fall back to locale.
    @Suppress("DEPRECATION")
    val locale = subtype.locale.orEmpty()
    return if (locale.lowercase().startsWith("hi")) "hi" else "en"
  }
}
