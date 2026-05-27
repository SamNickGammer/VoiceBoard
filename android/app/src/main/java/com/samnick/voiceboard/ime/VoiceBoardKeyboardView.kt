package com.samnick.voiceboard.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.samnick.voiceboard.R

class VoiceBoardKeyboardView(context: Context) : LinearLayout(context) {

  interface Listener {
    fun onKey(char: Char)
    fun onBackspace()
    fun onEnter()
    fun onSpace()
    fun onMicTap()
    fun onSwitchIme()
    fun onModeSelected(mode: String)
  }

  var listener: Listener? = null

  private val modes = listOf("default" to "Default", "formal" to "Formal", "generate" to "Generate")
  private val modePills = mutableMapOf<String, TextView>()
  private var currentMode: String = "default"
  private var status: String = ""

  private val micButton: FrameLayout
  private val micIcon: ImageView
  private val statusLabel: TextView
  private var pulseAnimator: ValueAnimator? = null

  init {
    orientation = VERTICAL
    setBackgroundColor(Color.parseColor("#F2F3F5"))

    val toolbar = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(52))
      setPadding(dp(8), dp(6), dp(8), dp(6))
    }

    micButton = FrameLayout(context).apply {
      layoutParams = LayoutParams(dp(40), dp(40))
      background = context.getDrawable(R.drawable.bg_mic_button)
      isClickable = true
      isFocusable = true
      setOnClickListener { listener?.onMicTap() }
    }
    micIcon = ImageView(context).apply {
      layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
      setImageResource(R.drawable.ic_mic)
      setColorFilter(Color.WHITE)
    }
    micButton.addView(micIcon)
    toolbar.addView(micButton)

    val pillsRow = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        leftMargin = dp(8)
        rightMargin = dp(8)
      }
    }
    modes.forEach { (id, label) ->
      val pill = TextView(context).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
          rightMargin = dp(6)
        }
        setOnClickListener {
          setMode(id)
          listener?.onModeSelected(id)
        }
      }
      modePills[id] = pill
      pillsRow.addView(pill)
    }
    toolbar.addView(pillsRow)

    val switchImeButton = ImageButton(context).apply {
      layoutParams = LayoutParams(dp(36), dp(36))
      setImageResource(R.drawable.ic_keyboard)
      background = null
      setOnClickListener { listener?.onSwitchIme() }
    }
    toolbar.addView(switchImeButton)

    addView(toolbar)

    statusLabel = TextView(context).apply {
      textSize = 11f
      setTextColor(Color.parseColor("#6B7280"))
      gravity = Gravity.CENTER
      visibility = View.GONE
      setPadding(dp(8), dp(2), dp(8), dp(2))
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    addView(statusLabel)

    addView(buildKeyboardArea())
    applyModePillStyles()
  }

  private fun buildKeyboardArea(): View {
    val container = LinearLayout(context).apply {
      orientation = VERTICAL
      setPadding(dp(4), dp(4), dp(4), dp(8))
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    val rows = listOf(
        "qwertyuiop".toList(),
        "asdfghjkl".toList(),
        "zxcvbnm".toList(),
    )
    rows.forEach { letters ->
      val row = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
          topMargin = dp(4)
        }
      }
      letters.forEach { ch ->
        row.addView(letterKey(ch))
      }
      container.addView(row)
    }

    val bottomRow = LinearLayout(context).apply {
      orientation = HORIZONTAL
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
        topMargin = dp(4)
      }
    }
    bottomRow.addView(specialKey("123", weight = 1.5f) { /* no-op in phase 1 */ })
    bottomRow.addView(specialKey(",", weight = 1f) { listener?.onKey(',') })
    bottomRow.addView(specialKey("space", weight = 4f) { listener?.onSpace() })
    bottomRow.addView(specialKey(".", weight = 1f) { listener?.onKey('.') })
    bottomRow.addView(iconKey(R.drawable.ic_backspace, weight = 1.5f) { listener?.onBackspace() })
    container.addView(bottomRow)

    val finalRow = LinearLayout(context).apply {
      orientation = HORIZONTAL
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
        topMargin = dp(4)
      }
    }
    finalRow.addView(specialKey("return", weight = 1f) { listener?.onEnter() })
    container.addView(finalRow)

    return container
  }

  private fun letterKey(ch: Char): View =
      TextView(context).apply {
        text = ch.toString()
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#111827"))
        background = context.getDrawable(R.drawable.bg_key)
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            .apply { setMargins(dp(2), 0, dp(2), 0) }
        setOnClickListener { listener?.onKey(ch) }
      }

  private fun specialKey(label: String, weight: Float, onClick: () -> Unit): View =
      TextView(context).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#374151"))
        background = context.getDrawable(R.drawable.bg_key)
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            .apply { setMargins(dp(2), 0, dp(2), 0) }
        setOnClickListener { onClick() }
      }

  private fun iconKey(resId: Int, weight: Float, onClick: () -> Unit): View =
      ImageButton(context).apply {
        setImageResource(resId)
        background = context.getDrawable(R.drawable.bg_key)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            .apply { setMargins(dp(2), 0, dp(2), 0) }
        setOnClickListener { onClick() }
      }

  fun setMode(mode: String) {
    currentMode = mode
    applyModePillStyles()
  }

  private fun applyModePillStyles() {
    modePills.forEach { (id, pill) ->
      if (id == currentMode) {
        pill.background = context.getDrawable(R.drawable.bg_pill_active)
        pill.setTextColor(Color.WHITE)
      } else {
        pill.background = context.getDrawable(R.drawable.bg_pill_inactive)
        pill.setTextColor(Color.parseColor("#374151"))
      }
    }
  }

  fun setRecording(recording: Boolean) {
    if (recording) {
      if (pulseAnimator?.isStarted == true) return
      val anim = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
          val v = it.animatedValue as Float
          micButton.scaleX = v
          micButton.scaleY = v
        }
      }
      pulseAnimator = anim
      anim.start()
      setStatus("Listening...")
    } else {
      pulseAnimator?.cancel()
      pulseAnimator = null
      micButton.scaleX = 1f
      micButton.scaleY = 1f
      if (status == "Listening...") setStatus("")
    }
  }

  fun setStatus(text: String) {
    status = text
    statusLabel.text = text
    statusLabel.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
  }

  private fun dp(value: Int): Int =
      TypedValue.applyDimension(
          TypedValue.COMPLEX_UNIT_DIP,
          value.toFloat(),
          resources.displayMetrics,
      ).toInt()
}
