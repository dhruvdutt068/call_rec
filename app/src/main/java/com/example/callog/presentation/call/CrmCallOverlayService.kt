package com.example.callog.presentation.call

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.example.callog.R
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.service.CallSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mode B: Smart CRM Overlay / HUD Service.
 *
 * Implements Phase 9:
 * - Floating, draggable CRM HUD window for non-obstructive caller and lead context
 * - Respects SYSTEM_ALERT_WINDOW permission
 * - Safely dismisses on call termination or manual close
 * - Touch-drag positioning without obstructing native dialer controls
 */
@AndroidEntryPoint
class CrmCallOverlayService : Service() {

    companion object {
        private const val TAG = "CrmCallOverlayService"
        const val ACTION_SHOW_OVERLAY = "com.example.callog.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.callog.ACTION_HIDE_OVERLAY"

        fun isOverlaySupported(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun show(context: Context) {
            if (isOverlaySupported(context)) {
                val intent = Intent(context, CrmCallOverlayService::class.java).apply {
                    action = ACTION_SHOW_OVERLAY
                }
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, CrmCallOverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var callSessionManager: CallSessionManager

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var sessionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        observeCallSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_OVERLAY -> {
                removeOverlayView()
                stopSelf()
            }
            ACTION_SHOW_OVERLAY -> {
                createOrUpdateOverlay()
            }
            else -> {
                createOrUpdateOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeCallSession() {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            callSessionManager.activeCallSession.collectLatest { active ->
                if (active == null) {
                    DeveloperLogger.info(TAG, "No active call session remaining, auto-closing CRM HUD overlay.")
                    removeOverlayView()
                    stopSelf()
                } else {
                    updateViewContent(
                        name = active.callerDisplayName.ifBlank { active.phoneNumber },
                        leadStatus = active.crmStatus.name,
                        company = active.companyName ?: ""
                    )
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOrUpdateOverlay() {
        if (!isOverlaySupported(this)) {
            DeveloperLogger.warning(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            stopSelf()
            return
        }

        if (overlayView == null) {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.view_crm_call_overlay, null, false)

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 32
                y = 120
            }

            // Draggable touch listener
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, params)
                        true
                    }
                    else -> false
                }
            }

            view.findViewById<ImageButton?>(R.id.btn_close_overlay)?.setOnClickListener {
                removeOverlayView()
                stopSelf()
            }

            try {
                windowManager?.addView(view, params)
                overlayView = view
                layoutParams = params
                DeveloperLogger.info(TAG, "CRM HUD overlay view attached to WindowManager")
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Failed to attach overlay view: ${e.message}", e)
                stopSelf()
            }
        }
    }

    private fun updateViewContent(name: String, leadStatus: String, company: String) {
        overlayView?.let { view ->
            view.findViewById<TextView?>(R.id.tv_caller_name)?.text = name
            view.findViewById<TextView?>(R.id.tv_lead_badge)?.text = leadStatus
            view.findViewById<TextView?>(R.id.tv_caller_company)?.text = company
        }
    }

    private fun removeOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                DeveloperLogger.info(TAG, "CRM HUD overlay view removed from WindowManager")
            } catch (e: Exception) {
                DeveloperLogger.warning(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionJob?.cancel()
        removeOverlayView()
    }
}
