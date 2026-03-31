package com.me.zapret.android.service

import android.net.VpnService
import com.me.zapret.android.core.CoreBridge
import com.me.zapret.android.core.DecisionAction
import com.me.zapret.android.core.FlowKey
import com.me.zapret.android.core.Protocol
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

data class SelfTestProbe(
    val host: String,
    val port: Int = 443,
    val useTls: Boolean = true,
)

data class SelfTestProbeResult(
    val target: String,
    val status: String,
    val latencyMs: Long,
    val matchedRuleId: String?,
    val effectivePreset: String?,
)

class ProtectedSelfTestRunner(
    private val vpnService: VpnService,
    private val coreBridge: CoreBridge,
) {
    fun runApplyProbe(
        engineHandle: Long,
        probe: SelfTestProbe,
    ): SelfTestProbeResult {
        val start = System.currentTimeMillis()
        val decision = coreBridge.inspectEarlyBytes(
            engineHandle = engineHandle,
            flowKey = FlowKey(
                proto = Protocol.TCP,
                srcIp = "10.0.0.2",
                srcPort = 0,
                dstIp = probe.host,
                dstPort = probe.port,
                direction = com.me.zapret.android.core.Direction.OUTBOUND,
            ),
            bufferedBytes = "HEAD / HTTP/1.1\r\nHost: ${probe.host}\r\n\r\n".encodeToByteArray(),
            nowMs = start,
        )
        if (decision.action == DecisionAction.BLOCK) {
            return SelfTestProbeResult(
                target = "${probe.host}:${probe.port}",
                status = "BLOCKED",
                latencyMs = System.currentTimeMillis() - start,
                matchedRuleId = decision.matchedRuleId,
                effectivePreset = decision.effectivePreset.name,
            )
        }
        val socket = Socket()
        vpnService.protect(socket)
        socket.connect(InetSocketAddress(probe.host, probe.port), 5_000)
        if (probe.useTls) {
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, probe.host, probe.port, true)
            sslSocket.startHandshake()
            sslSocket.close()
        } else {
            socket.close()
        }
        return SelfTestProbeResult(
            target = "${probe.host}:${probe.port}",
            status = if (decision.action == DecisionAction.BLOCK) "BLOCKED" else "OK",
            latencyMs = System.currentTimeMillis() - start,
            matchedRuleId = decision.matchedRuleId,
            effectivePreset = decision.effectivePreset.name,
        )
    }
}
