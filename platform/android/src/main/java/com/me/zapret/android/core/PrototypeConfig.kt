package com.me.zapret.android.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val PROTOTYPE_SCHEMA_VERSION = 1
private const val PROTOTYPE_APP_VERSION = "0.1.0"
private const val PROTOTYPE_CORE_VERSION = "0.1.0-kotlin"

@Serializable
data class PrototypeConfigDocument(
    @SerialName("app_version") val appVersion: String,
    @SerialName("core_version") val coreVersion: String,
    @SerialName("config_schema_version") val configSchemaVersion: Int,
    @SerialName("feature_flags") val featureFlags: FeatureFlagsDocument,
    @SerialName("active_profile_id") val activeProfileId: String,
    val profiles: List<ProfileDocument>,
    val settings: SettingsDocument,
    @SerialName("last_valid_config_snapshot") val lastValidConfigSnapshot: JsonElement? = null,
)

@Serializable
data class FeatureFlagsDocument(
    @SerialName("enable_tls_sni") val enableTlsSni: Boolean,
    @SerialName("enable_http_host") val enableHttpHost: Boolean,
    @SerialName("enable_aggressive_split") val enableAggressiveSplit: Boolean,
    @SerialName("enable_self_test_ab") val enableSelfTestAb: Boolean,
)

@Serializable
data class SettingsDocument(
    val language: String,
    @SerialName("theme_mode") val themeMode: String,
    @SerialName("debug_logs") val debugLogs: Boolean,
    @SerialName("safe_mode_enabled") val safeModeEnabled: Boolean,
    val updates: UpdateSettingsDocument,
)

@Serializable
data class UpdateSettingsDocument(
    @SerialName("repo_reference") val repoReference: String,
    @SerialName("api_base_url") val apiBaseUrl: String,
    @SerialName("auto_update") val autoUpdate: Boolean,
    @SerialName("wifi_only") val wifiOnly: Boolean,
    @SerialName("apply_on_next_vpn_start") val applyOnNextVpnStart: Boolean,
)

@Serializable
data class ProfileDocument(
    val id: String,
    val name: String,
    val preset: Preset,
    @SerialName("custom_techniques") val customTechniques: CustomTechniquesDocument? = null,
    val rules: List<RuleDocument>,
    @SerialName("quic_policy") val quicPolicy: QuicPolicy,
    @SerialName("fallback_policy") val fallbackPolicy: FallbackPolicyDocument,
)

@Serializable
data class CustomTechniquesDocument(
    @SerialName("http_split_offsets") val httpSplitOffsets: List<Int>,
    @SerialName("tls_split_offsets") val tlsSplitOffsets: List<Int>,
)

@Serializable
data class FallbackPolicyDocument(
    @SerialName("aggressive_threshold") val aggressiveThreshold: Int,
    @SerialName("balanced_threshold") val balancedThreshold: Int,
    @SerialName("time_window_seconds") val timeWindowSeconds: Long,
)

@Serializable
data class RuleDocument(
    val id: String,
    val priority: Int,
    val action: DecisionAction,
    @SerialName("match") val match: RuleMatchDocument,
)

@Serializable
data class RuleMatchDocument(
    val protocol: Protocol = Protocol.ANY,
    @SerialName("domain_exact") val domainExact: String? = null,
    @SerialName("domain_wildcard") val domainWildcard: String? = null,
    val cidr: String? = null,
    @SerialName("port_range") val portRange: PortRangeDocument? = null,
)

@Serializable
data class PortRangeDocument(
    val start: Int,
    val end: Int,
)

object PrototypeConfigFactory {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun sampleConfigDocument(): PrototypeConfigDocument =
        PrototypeConfigDocument(
            appVersion = PROTOTYPE_APP_VERSION,
            coreVersion = PROTOTYPE_CORE_VERSION,
            configSchemaVersion = PROTOTYPE_SCHEMA_VERSION,
            featureFlags = FeatureFlagsDocument(
                enableTlsSni = true,
                enableHttpHost = true,
                enableAggressiveSplit = true,
                enableSelfTestAb = true,
            ),
            activeProfileId = "default",
            profiles = listOf(
                ProfileDocument(
                    id = "default",
                    name = "Balanced",
                    preset = Preset.Balanced,
                    rules = listOf(
                        RuleDocument(
                            id = "apply-example",
                            priority = 10,
                            action = DecisionAction.APPLY,
                            match = RuleMatchDocument(
                                protocol = Protocol.TCP,
                                domainExact = "example.com",
                                portRange = PortRangeDocument(443, 443),
                            ),
                        ),
                        RuleDocument(
                            id = "direct-bank",
                            priority = 20,
                            action = DecisionAction.DIRECT,
                            match = RuleMatchDocument(
                                protocol = Protocol.ANY,
                                domainWildcard = "*.bank.example",
                            ),
                        ),
                        RuleDocument(
                            id = "block-debug",
                            priority = 30,
                            action = DecisionAction.BLOCK,
                            match = RuleMatchDocument(
                                protocol = Protocol.TCP,
                                domainExact = "blocked.example",
                            ),
                        ),
                    ),
                    quicPolicy = QuicPolicy.BlockUdp443ForApply,
                    fallbackPolicy = FallbackPolicyDocument(
                        aggressiveThreshold = 3,
                        balancedThreshold = 2,
                        timeWindowSeconds = 300,
                    ),
                ),
            ),
            settings = SettingsDocument(
                language = "EN",
                themeMode = "SYSTEM",
                debugLogs = false,
                safeModeEnabled = true,
                updates = UpdateSettingsDocument(
                    repoReference = "owner/repo",
                    apiBaseUrl = "https://api.github.com",
                    autoUpdate = true,
                    wifiOnly = true,
                    applyOnNextVpnStart = true,
                ),
            ),
        )

    fun sampleConfigJson(): String = json.encodeToString(sampleConfigDocument())
}

