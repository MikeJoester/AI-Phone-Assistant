package com.ondeviceaiassistantapp

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Activity
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class OverlayManagerModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    companion object {
        var reactAppContext: ReactApplicationContext? = null
    }

    init {
        reactAppContext = reactContext
        reactContext.addActivityEventListener(this)
    }

    private var pickerPromise: Promise? = null

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var triggerOverlayView: LinearLayout? = null
    private var textView: TextView? = null

    override fun getName(): String {
        return "OverlayManager"
    }

    @ReactMethod
    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${reactContext.packageName}"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                reactContext.startActivity(intent)
            }
        }
    }

    @ReactMethod
    fun pickModelFile(promise: Promise) {
        val currentActivity = reactContext.currentActivity
        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist")
            return
        }
        pickerPromise = promise
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            currentActivity.startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            pickerPromise?.reject("E_FAILED_TO_SHOW_PICKER", e.message)
            pickerPromise = null
        }
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001) {
            if (pickerPromise != null) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    var filePath = ""
                    if (uri != null) {
                        val path = uri.path
                        if (path != null && path.startsWith("/document/raw:")) {
                            filePath = path.substring("/document/raw:".length)
                        } else if (path != null && path.startsWith("/document/primary:")) {
                            filePath = "/storage/emulated/0/" + path.substring("/document/primary:".length)
                        } else {
                            filePath = uri.toString()
                        }
                    }
                    pickerPromise?.resolve(filePath)
                } else {
                    pickerPromise?.reject("E_PICKER_CANCELLED", "File picker was cancelled")
                }
                pickerPromise = null
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {}

    @ReactMethod
    fun showTriggerButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
            return
        }

        reactContext.runOnUiQueueThread {
            if (triggerOverlayView == null) {
                if (windowManager == null) {
                    windowManager = reactContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                }
                
                val button = Button(reactContext).apply {
                    setText("🤖 Draft AI")
                    setBackgroundColor(Color.parseColor("#88000000"))
                    setTextColor(Color.WHITE)
                    setPadding(16, 16, 16, 16)
                    setOnClickListener {
                        reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                            .emit("onManualTriggerClicked", null)
                    }
                }
                
                triggerOverlayView = LinearLayout(reactContext).apply {
                    addView(button)
                }

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )

                params.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                windowManager?.addView(triggerOverlayView, params)
            }
        }
    }

    @ReactMethod
    fun showOverlay(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${reactContext.packageName}"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            reactContext.startActivity(intent)
            return
        }

        reactContext.runOnUiQueueThread {
            if (overlayView == null) {
                windowManager = reactContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                overlayView = LinearLayout(reactContext).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.parseColor("#E6000000")) 
                    setPadding(32, 32, 32, 32)
                }

                textView = TextView(reactContext).apply {
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(0, 0, 0, 32)
                }

                val buttonContainer = LinearLayout(reactContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }

                val approveButton = Button(reactContext).apply {
                    setText("Approve")
                    setOnClickListener {
                        hideOverlayUI()
                        val params = Arguments.createMap()
                        params.putString("text", textView?.text.toString())
                        reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                            .emit("onApproveClicked", params)
                    }
                }

                val rejectButton = Button(reactContext).apply {
                    setText("Reject")
                    setOnClickListener {
                        hideOverlayUI()
                    }
                }
                
                buttonContainer.addView(approveButton)
                buttonContainer.addView(rejectButton)
                
                overlayView?.addView(textView)
                overlayView?.addView(buttonContainer)

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )

                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                windowManager?.addView(overlayView, params)
            }
            textView?.setText(text)
            overlayView?.visibility = android.view.View.VISIBLE
        }
    }

    @ReactMethod
    fun hideOverlay() {
        reactContext.runOnUiQueueThread {
            hideOverlayUI()
        }
    }

    private fun hideOverlayUI() {
        overlayView?.visibility = android.view.View.GONE
    }
    
    @ReactMethod
    fun injectText(text: String) {
        ChatReaderAccessibilityService.instance?.injectText(text)
    }
}
