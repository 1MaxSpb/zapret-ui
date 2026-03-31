package com.me.zapret.android.service

import com.me.zapret.android.core.DecisionAction
import com.me.zapret.android.core.EarlyDecision
import com.me.zapret.android.core.FlowKey

data class UdpHandlingResult(
    val shouldBlock: Boolean,
    val reason: String? = null,
)

class UdpDirectForwarder {
    fun resolve(flowKey: FlowKey, decision: EarlyDecision): UdpHandlingResult {
        if (flowKey.dstPort == 443 && decision.action == DecisionAction.BLOCK) {
            return UdpHandlingResult(
                shouldBlock = true,
                reason = "QUIC blocked for APPLY rule",
            )
        }
        return UdpHandlingResult(shouldBlock = false)
    }
}

