package com.me.zapret.android.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FlowKey(
    @SerialName("proto") val proto: Protocol,
    @SerialName("src_ip") val srcIp: String,
    @SerialName("src_port") val srcPort: Int,
    @SerialName("dst_ip") val dstIp: String,
    @SerialName("dst_port") val dstPort: Int,
    @SerialName("direction") val direction: Direction,
)

@Serializable
enum class Protocol {
    TCP,
    UDP,
    ANY,
}

@Serializable
enum class Direction {
    OUTBOUND,
    INBOUND,
}

@Serializable
enum class DecisionState {
    NEED_MORE_DATA,
    FINAL,
}

@Serializable
enum class DecisionAction {
    APPLY,
    DIRECT,
    BLOCK,
}

@Serializable
enum class Preset {
    Compatible,
    Balanced,
    Aggressive,
    Custom,
}

@Serializable
enum class QuicPolicy {
    Allow,
    BlockUdp443ForApply,
}

@Serializable
data class SplitPlan(
    val strategy: String,
    val offsets: List<Int>,
)

@Serializable
data class EarlyDecision(
    val state: DecisionState = DecisionState.FINAL,
    val action: DecisionAction = DecisionAction.DIRECT,
    @SerialName("effective_preset") val effectivePreset: Preset = Preset.Balanced,
    @SerialName("split_plan") val splitPlan: SplitPlan? = null,
    @SerialName("quic_policy") val quicPolicy: QuicPolicy = QuicPolicy.Allow,
    @SerialName("matched_rule_id") val matchedRuleId: String? = null,
    val hostname: String? = null,
    val error: String? = null,
)

@Serializable
data class NativeResponse(
    val ok: Boolean,
    val error: String? = null,
    @SerialName("engineHandle") val engineHandle: Long? = null,
    @SerialName("schemaVersion") val schemaVersion: Int? = null,
    @SerialName("activeProfileId") val activeProfileId: String? = null,
)

@Serializable
data class FlowResultReport(
    @SerialName("rule_id") val ruleId: String? = null,
    val hostname: String? = null,
    @SerialName("result_code") val resultCode: FlowResultCode,
    @SerialName("now_ms") val nowMs: Long,
)

@Serializable
enum class FlowResultCode {
    Success,
    Timeout,
    Reset,
    HandshakeFail,
}

