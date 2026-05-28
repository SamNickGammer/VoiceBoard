package com.samnick.voiceboard.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tiny popup floated above the pill when the user long-presses it.
 * Lists the three Claude modes; tapping one calls back and dismisses.
 */
class ModeMenuView(
    context: Context,
    private val onPick: (String) -> Unit,
) : LinearLayout(context) {

  init {
    orientation = VERTICAL
    val bg = GradientDrawable().apply {
      cornerRadius = dp(12f)
      setColor(Color.parseColor("#FF1F2937"))
    }
    background = bg
    setPadding(dp(8f).toInt(), dp(6f).toInt(), dp(8f).toInt(), dp(6f).toInt())

    listOf("default", "formal", "generate").forEach { mode ->
      addView(TextView(context).apply {
        text = mode.replaceFirstChar { it.uppercase() }
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10f).toInt(), dp(10f).toInt(), dp(10f).toInt(), dp(10f).toInt())
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onPick(mode) }
      })
    }
  }

  private fun dp(v: Float): Float =
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
