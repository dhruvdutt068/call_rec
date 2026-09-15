package com.example.callog.core.telecom

import android.telecom.Call
import com.example.callog.domain.call.CallCapabilities
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallState

/**
 * Internal Telecom wrapper binding an active Android Telecom [Call] object
 * with its registered [Call.Callback] and state.
 */
data class ManagedCall(
    val callId: String,
    val rawCall: Call,
    val callback: Call.Callback,
    val direction: CallDirection,
    val state: CallState,
    val capabilities: CallCapabilities,
    val addedTimeMillis: Long = System.currentTimeMillis()
)
