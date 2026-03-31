package com.me.zapret.android.service

import com.me.zapret.android.core.CoreBridge
import com.me.zapret.android.core.DecisionAction
import com.me.zapret.android.core.DecisionState
import com.me.zapret.android.core.EarlyDecision
import com.me.zapret.android.core.FlowKey

class TcpFlowCoordinator(
    private val coreBridge: CoreBridge,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun inspect(
        engineHandle: Long,
        flowKey: FlowKey,
        bufferedBytes: ByteArray,
        firstPayloadAtMs: Long,
    ): EarlyInspectionOutcome {
        val nowMs = clock()
        val decision = coreBridge.inspectEarlyBytes(
            engineHandle = engineHandle,
            flowKey = flowKey,
            bufferedBytes = bufferedBytes,
            nowMs = nowMs,
        )
        val limitReached =
            bufferedBytes.size >= EARLY_BUFFER_LIMIT_BYTES || (nowMs - firstPayloadAtMs) >= EARLY_BUFFER_LIMIT_MS

        if (decision.state == DecisionState.NEED_MORE_DATA && !limitReached) {
            return EarlyInspectionOutcome.BufferMore
        }

        if (decision.state == DecisionState.NEED_MORE_DATA && limitReached) {
            return EarlyInspectionOutcome.Final(
                decision = decision.copy(
                    state = DecisionState.FINAL,
                    action = DecisionAction.DIRECT,
                    error = decision.error ?: "early buffer limit reached before final match",
                ),
            )
        }

        return EarlyInspectionOutcome.Final(decision)
    }

    companion object {
        const val EARLY_BUFFER_LIMIT_BYTES = 8 * 1024
        const val EARLY_BUFFER_LIMIT_MS = 500L
    }
}

sealed class EarlyInspectionOutcome {
    data object BufferMore : EarlyInspectionOutcome()
    data class Final(val decision: EarlyDecision) : EarlyInspectionOutcome()
}

