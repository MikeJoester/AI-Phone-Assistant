package com.ondeviceaiassistantapp

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule

class ChatReaderAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ChatReaderAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    private val recentlySeenTexts = mutableSetOf<String>()
    private var lastClearTime = System.currentTimeMillis()
    private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) return

        // NEVER read our own app's UI to prevent infinite recursive reading loops
        if (event.packageName?.toString() == "com.ondeviceaiassistantapp") return

        if (System.currentTimeMillis() - lastClearTime > 10000) {
            recentlySeenTexts.clear()
            lastClearTime = System.currentTimeMillis()
        }

        val rootNode = rootInActiveWindow ?: return
        val screenTextBuilder = java.lang.StringBuilder()
        extractAllText(rootNode, screenTextBuilder)
        
        val fullScreenText = screenTextBuilder.toString().trim()
        if (fullScreenText.length > 10) {
            // Cancel previous background timer
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }

            // Set new 3.5 second native background timer
            debounceRunnable = Runnable {
                if (recentlySeenTexts.add(fullScreenText)) {
                    emitEventToJS("onMessageDetected", fullScreenText)
                }
            }
            debounceHandler.postDelayed(debounceRunnable!!, 3500)
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo, builder: java.lang.StringBuilder) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        // We capture any text longer than 1 character so we don't miss short usernames (e.g. "Bo") or short messages.
        if (!text.isNullOrBlank() && text.length > 1) {
            builder.append(text).append("\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { extractAllText(it, builder) }
        }
    }

    private fun emitEventToJS(eventName: String, message: String) {
        val reactContext = OverlayManagerModule.reactAppContext
        if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
            val params = Arguments.createMap()
            params.putString("text", message)
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        }
    }

    override fun onInterrupt() {}
    
    fun injectText(text: String) {
        // 1. Always copy to the Android Clipboard so the user can manually paste if needed
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI Draft", text)
        clipboard.setPrimaryClip(clip)

        // 2. Try to automatically paste it into the Discord text box
        val rootNode = rootInActiveWindow ?: return
        val editTextNode = findEditTextNode(rootNode)
        if (editTextNode != null) {
            // Discord ignores ACTION_SET_TEXT but respects ACTION_PASTE from the clipboard
            editTextNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    private fun findEditTextNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findEditTextNode(child)
                if (found != null) return found
            }
        }
        return null
    }
}
