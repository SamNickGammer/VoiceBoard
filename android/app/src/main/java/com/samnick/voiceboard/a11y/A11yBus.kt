package com.samnick.voiceboard.a11y

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton glue between [VoiceBoardAccessibilityService] (which can read the
 * window tree and inject text) and [com.samnick.voiceboard.overlay.OverlayService]
 * (which just needs to know "should I show the pill?" and "please inject this string").
 *
 * The accessibility service publishes the last focused editable node + a timestamp;
 * the overlay service reads these to decide visibility. Injection goes through the
 * one [injector] the accessibility service registers on start.
 */
object A11yBus {

  fun interface Injector {
    fun inject(text: String): Boolean
  }

  private val focusedNode = AtomicReference<AccessibilityNodeInfo?>()
  private val lastFocusEventMs = AtomicLong(0L)
  private val imeVisible = AtomicReference(false)
  private val injectorRef = AtomicReference<Injector?>()

  private val listeners = mutableSetOf<() -> Unit>()
  private val listenersLock = Any()

  fun setFocusedNode(node: AccessibilityNodeInfo?) {
    val prev = focusedNode.getAndSet(node)
    if (prev != null && prev !== node) {
      try { prev.recycle() } catch (_: Throwable) {}
    }
    if (node != null) lastFocusEventMs.set(SystemClock.elapsedRealtime())
    notifyListeners()
  }

  fun setImeVisible(visible: Boolean) {
    if (imeVisible.getAndSet(visible) != visible) notifyListeners()
  }

  fun isImeVisible(): Boolean = imeVisible.get() ?: false

  fun hasRecentFocusedEditable(maxAgeMs: Long = 30_000): Boolean {
    val node = focusedNode.get() ?: return false
    val age = SystemClock.elapsedRealtime() - lastFocusEventMs.get()
    return node.isEditable && age <= maxAgeMs
  }

  fun shouldShowPill(): Boolean = isImeVisible() && hasRecentFocusedEditable()

  fun setInjector(injector: Injector?) {
    injectorRef.set(injector)
  }

  fun injectText(text: String): Boolean = injectorRef.get()?.inject(text) ?: false

  fun focusedNode(): AccessibilityNodeInfo? = focusedNode.get()

  fun addListener(listener: () -> Unit) {
    synchronized(listenersLock) { listeners.add(listener) }
  }

  fun removeListener(listener: () -> Unit) {
    synchronized(listenersLock) { listeners.remove(listener) }
  }

  private fun notifyListeners() {
    val snapshot = synchronized(listenersLock) { listeners.toList() }
    snapshot.forEach { runCatching { it() } }
  }
}
