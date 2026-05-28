package com.samnick.voiceboard.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Floating pill drawn into the system overlay. Behaviour:
 * - tap: [Listener.onTap]
 * - long-press: [Listener.onLongPress]
 * - drag: [Listener.onDrag] with delta x/y in px
 *
 * States: IDLE (purple), RECORDING (animated pulse), THINKING (spinner-ish pulse).
 */
class OverlayPillView(context: Context) : View(context) {

  enum class State { IDLE, RECORDING, THINKING }

  interface Listener {
    fun onTap()
    fun onDrag(dx: Float, dy: Float)
    fun onDragEnd()
  }

  var listener: Listener? = null

  private var state: State = State.IDLE
  private var pulse: Float = 0f
  private var pulseAnimator: ValueAnimator? = null

  private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#7F77DD")
    style = Paint.Style.FILL
  }
  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#A8A0F0")
    style = Paint.Style.STROKE
    strokeWidth = dp(2f)
  }
  private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.FILL
  }

  private val gestures = GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
          listener?.onTap(); return true
        }
        // Intentionally no onLongPress — mode picker lives in the notification
        // panel so a slow drag doesn't accidentally pop a menu over the pill.
      },
  )

  private var downRawX = 0f
  private var downRawY = 0f
  private var dragging = false
  private val touchSlop = dp(6f)

  init {
    isClickable = true
    isFocusable = true
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downRawX = event.rawX; downRawY = event.rawY; dragging = false
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
          dragging = true
        }
        if (dragging) {
          listener?.onDrag(dx, dy)
          downRawX = event.rawX; downRawY = event.rawY
          return true
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (dragging) {
          listener?.onDragEnd()
          dragging = false
          return true
        }
      }
    }
    return gestures.onTouchEvent(event) || super.onTouchEvent(event)
  }

  fun setState(newState: State) {
    if (state == newState) return
    state = newState
    when (newState) {
      State.IDLE -> {
        pulseAnimator?.cancel(); pulseAnimator = null; pulse = 0f
        pillPaint.color = Color.parseColor("#7F77DD")
        invalidate()
      }
      State.RECORDING -> {
        pillPaint.color = Color.parseColor("#EF4444")
        startPulse()
      }
      State.THINKING -> {
        pillPaint.color = Color.parseColor("#7F77DD")
        startPulse()
      }
    }
  }

  private fun startPulse() {
    pulseAnimator?.cancel()
    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 1100
      repeatCount = ValueAnimator.INFINITE
      addUpdateListener {
        pulse = it.animatedValue as Float
        invalidate()
      }
    }
    pulseAnimator = anim
    anim.start()
  }

  override fun onDraw(canvas: Canvas) {
    val w = width.toFloat(); val h = height.toFloat()
    val pillRect = RectF(dp(6f), dp(6f), w - dp(6f), h - dp(6f))
    val r = pillRect.height() / 2f

    if (state != State.IDLE) {
      val expand = dp(8f) * (1f - kotlin.math.abs(2f * pulse - 1f))
      val ringRect = RectF(
          pillRect.left - expand,
          pillRect.top - expand,
          pillRect.right + expand,
          pillRect.bottom + expand,
      )
      val ringR = ringRect.height() / 2f
      ringPaint.alpha = (180 * (1f - pulse)).toInt().coerceIn(0, 255)
      canvas.drawRoundRect(ringRect, ringR, ringR, ringPaint)
    }

    canvas.drawRoundRect(pillRect, r, r, pillPaint)

    // Single dot in the centre — keeps the bar minimal like the gesture-nav home bar.
    val cx = w / 2f
    val cy = h / 2f
    canvas.drawCircle(cx, cy, dp(4f), dotPaint)
  }

  override fun onDetachedFromWindow() {
    pulseAnimator?.cancel()
    pulseAnimator = null
    super.onDetachedFromWindow()
  }

  private fun dp(v: Float): Float =
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
