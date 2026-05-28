package com.samnick.voiceboard.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class VoiceBoardAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    serviceInfo = serviceInfo.apply {
      eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
          AccessibilityEvent.TYPE_WINDOWS_CHANGED or
          AccessibilityEvent.TYPE_VIEW_CLICKED
      flags = flags or
          AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
          AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
          AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      notificationTimeout = 100
    }

    A11yBus.setInjector { text ->
      val node = A11yBus.focusedNode() ?: return@setInjector false
      val args = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
      }
      try {
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
      } catch (_: Throwable) {
        false
      }
    }

    // Initial keyboard-visibility check.
    A11yBus.setImeVisible(detectImeVisible())
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    when (event.eventType) {
      AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_VIEW_CLICKED -> {
        val src = event.source ?: return
        if (src.isEditable) {
          A11yBus.setFocusedNode(src)
        } else {
          src.recycle()
        }
      }
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
      AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
        A11yBus.setImeVisible(detectImeVisible())
      }
    }
  }

  override fun onInterrupt() {}

  override fun onUnbind(intent: Intent?): Boolean {
    A11yBus.setInjector(null)
    A11yBus.setImeVisible(false)
    A11yBus.setFocusedNode(null)
    return super.onUnbind(intent)
  }

  private fun detectImeVisible(): Boolean {
    val windows = try { windows } catch (_: Throwable) { return false } ?: return false
    return windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
  }

  companion object {
    fun isEnabled(context: Context): Boolean {
      val enabled = Settings.Secure.getString(
          context.contentResolver,
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false
      val flat = "${context.packageName}/${VoiceBoardAccessibilityService::class.java.name}"
      val short = "${context.packageName}/.a11y.VoiceBoardAccessibilityService"
      return enabled.split(':').any { it == flat || it == short }
    }
  }
}
