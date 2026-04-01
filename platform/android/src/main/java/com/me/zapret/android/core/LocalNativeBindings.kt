package com.me.zapret.android.core

import java.math.BigInteger
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val LOCAL_CORE_VERSION = "0.1.0-kotlin"
private const val LOCAL_SCHEMA_VERSION = 1

private data class LoadedEngine(
    val config: PrototypeConfigDocument,
    val failures: MutableMap<String, MutableList<Long>> = mutableMapOf(),
)

private data class HostExtraction(
    val hostname: String? = null,
    val needsMoreData: Boolean = false,
    val trafficKind: TrafficKind = TrafficKind.Unknown,
)

private enum class TrafficKind {
    Http,
    Tls,
    Unknown,
}

class LocalNativeBindings(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : NativeBindings {
    override fun getCoreVersionJson(): String =
        """{"coreVersion":"$LOCAL_CORE_VERSION"}"""

    override fun getSchemaVersion(): Int = LOCAL_SCHEMA_VERSION

    override fun validateConfig(configJson: String): String =
        runCatching {
            val config = parseAndValidate(configJson)
            json.encodeToString(
                NativeResponse(
                    ok = true,
                    activeProfileId = config.activeProfileId,
                    schemaVersion = config.configSchemaVersion,
                ),
            )
        }.getOrElse { error ->
            json.encodeToString(NativeResponse(ok = false, error = error.message ?: "Config validation failed"))
        }

    override fun loadConfig(configJson: String): String =
        runCatching {
            val config = parseAndValidate(configJson)
            val handle = nextHandle.getAndIncrement()
            engines[handle] = LoadedEngine(config)
            json.encodeToString(
                NativeResponse(
                    ok = true,
                    engineHandle = handle,
                    activeProfileId = config.activeProfileId,
                    schemaVersion = config.configSchemaVersion,
                ),
            )
        }.getOrElse { error ->
            json.encodeToString(NativeResponse(ok = false, error = error.message ?: "Load config failed"))
        }

    override fun inspectEarlyBytes(
        engineHandle: Long,
        flowKeyJson: String,
        bufferedBytes: ByteArray,
        nowMs: Long,
    ): String =
        runCatching {
            val flowKey = json.decodeFromString<FlowKey>(flowKeyJson)
            val engine = engines[engineHandle] ?: error("Unknown engine handle: $engineHandle")
            json.encodeToString(inspect(engine, flowKey, bufferedBytes, nowMs))
        }.getOrElse { error ->
            json.encodeToString(
                EarlyDecision(
                    state = DecisionState.FINAL,
                    action = DecisionAction.DIRECT,
                    error = error.message ?: "inspectEarlyBytes failed",
                ),
            )
        }

    override fun reportFlowResult(engineHandle: Long, reportJson: String): Boolean =
        runCatching {
            val engine = engines[engineHandle] ?: return false
            val report = json.decodeFromString<FlowResultReport>(reportJson)
            val ruleId = report.ruleId ?: return true
            if (report.resultCode == FlowResultCode.Success) {
                engine.failures.remove(ruleId)
            } else {
                val timestamps = engine.failures.getOrPut(ruleId) { mutableListOf() }
                timestamps += report.nowMs
                val policy = activeProfile(engine.config).fallbackPolicy
                val cutoff = report.nowMs - policy.timeWindowSeconds * 1000
                timestamps.removeAll { it < cutoff }
            }
            true
        }.getOrDefault(false)

    override fun healthcheck(configJson: String): String =
        runCatching {
            val config = parseAndValidate(configJson)
            json.encodeToString(
                NativeResponse(
                    ok = true,
                    activeProfileId = config.activeProfileId,
                    schemaVersion = config.configSchemaVersion,
                ),
            )
        }.getOrElse { error ->
            json.encodeToString(NativeResponse(ok = false, error = error.message ?: "Healthcheck failed"))
        }

    override fun freeEngine(engineHandle: Long): Boolean = engines.remove(engineHandle) != null

    private fun parseAndValidate(configJson: String): PrototypeConfigDocument {
        val config = json.decodeFromString<PrototypeConfigDocument>(configJson)
        validate(config)
        return config
    }

    private fun validate(config: PrototypeConfigDocument) {
        require(config.configSchemaVersion == LOCAL_SCHEMA_VERSION) {
            "Unsupported config_schema_version: ${config.configSchemaVersion}"
        }
        require(isSemverLike(config.appVersion)) { "Invalid app_version: ${config.appVersion}" }
        require(isSemverLike(config.coreVersion)) { "Invalid core_version: ${config.coreVersion}" }

        val profileIds = mutableSetOf<String>()
        config.profiles.forEach { profile ->
            require(profileIds.add(profile.id)) { "Duplicate profile id: ${profile.id}" }
            val ruleIds = mutableSetOf<String>()
            profile.rules.forEach { rule ->
                require(ruleIds.add(rule.id)) { "Duplicate rule id in profile ${profile.id}: ${rule.id}" }
                rule.match.domainWildcard?.let { wildcard ->
                    require(wildcard.startsWith("*.") && wildcard.length > 3) {
                        "Invalid wildcard: $wildcard"
                    }
                }
                rule.match.portRange?.let { range ->
                    require(range.start <= range.end) {
                        "Invalid port range: ${range.start}-${range.end}"
                    }
                }
            }
        }

        require(config.profiles.any { it.id == config.activeProfileId }) {
            "Active profile does not exist: ${config.activeProfileId}"
        }
    }

    private fun inspect(
        engine: LoadedEngine,
        flowKey: FlowKey,
        bufferedBytes: ByteArray,
        nowMs: Long,
    ): EarlyDecision {
        val profile = activeProfile(engine.config)
        val extraction = extractTarget(bufferedBytes, engine.config.featureFlags)
        val rule = matchRule(profile, flowKey, extraction.hostname)

        if (flowKey.proto == Protocol.UDP) {
            return inspectUdp(profile, flowKey, rule)
        }

        if (rule == null) {
            return if (extraction.needsMoreData) {
                EarlyDecision(
                    state = DecisionState.NEED_MORE_DATA,
                    action = DecisionAction.DIRECT,
                    effectivePreset = profile.preset,
                    quicPolicy = profile.quicPolicy,
                    hostname = extraction.hostname,
                )
            } else {
                EarlyDecision(
                    state = DecisionState.FINAL,
                    action = DecisionAction.DIRECT,
                    effectivePreset = profile.preset,
                    quicPolicy = profile.quicPolicy,
                    hostname = extraction.hostname,
                )
            }
        }

        return when (rule.action) {
            DecisionAction.BLOCK -> EarlyDecision(
                state = DecisionState.FINAL,
                action = DecisionAction.BLOCK,
                effectivePreset = profile.preset,
                quicPolicy = profile.quicPolicy,
                matchedRuleId = rule.id,
                hostname = extraction.hostname,
            )

            DecisionAction.DIRECT -> EarlyDecision(
                state = DecisionState.FINAL,
                action = DecisionAction.DIRECT,
                effectivePreset = profile.preset,
                quicPolicy = profile.quicPolicy,
                matchedRuleId = rule.id,
                hostname = extraction.hostname,
            )

            DecisionAction.APPLY -> {
                if (extraction.hostname == null && extraction.needsMoreData) {
                    EarlyDecision(
                        state = DecisionState.NEED_MORE_DATA,
                        action = DecisionAction.DIRECT,
                        effectivePreset = profile.preset,
                        quicPolicy = profile.quicPolicy,
                        hostname = null,
                    )
                } else {
                    val effectivePreset = downgradePreset(engine, profile, rule.id, nowMs)
                    EarlyDecision(
                        state = DecisionState.FINAL,
                        action = DecisionAction.APPLY,
                        effectivePreset = effectivePreset,
                        splitPlan = buildSplitPlan(profile, effectivePreset, extraction.trafficKind, bufferedBytes.size),
                        quicPolicy = profile.quicPolicy,
                        matchedRuleId = rule.id,
                        hostname = extraction.hostname,
                    )
                }
            }
        }
    }

    private fun inspectUdp(
        profile: ProfileDocument,
        flowKey: FlowKey,
        rule: RuleDocument?,
    ): EarlyDecision {
        val action = when {
            rule == null -> DecisionAction.DIRECT
            rule.action == DecisionAction.BLOCK -> DecisionAction.BLOCK
            rule.action == DecisionAction.APPLY &&
                flowKey.dstPort == 443 &&
                profile.quicPolicy == QuicPolicy.BlockUdp443ForApply -> DecisionAction.BLOCK
            else -> DecisionAction.DIRECT
        }

        return EarlyDecision(
            state = DecisionState.FINAL,
            action = action,
            effectivePreset = profile.preset,
            quicPolicy = profile.quicPolicy,
            matchedRuleId = rule?.id,
        )
    }

    private fun matchRule(
        profile: ProfileDocument,
        flowKey: FlowKey,
        hostname: String?,
    ): RuleDocument? =
        profile.rules
            .sortedBy { it.priority }
            .firstOrNull { rule ->
                protocolMatches(rule.match.protocol, flowKey.proto) &&
                    domainMatches(rule.match, hostname) &&
                    cidrMatches(rule.match.cidr, flowKey.dstIp) &&
                    portMatches(rule.match.portRange, flowKey.dstPort)
            }

    private fun protocolMatches(ruleProtocol: Protocol, flowProtocol: Protocol): Boolean =
        ruleProtocol == Protocol.ANY || ruleProtocol == flowProtocol

    private fun domainMatches(ruleMatch: RuleMatchDocument, hostname: String?): Boolean {
        if (hostname == null) {
            return ruleMatch.domainExact == null && ruleMatch.domainWildcard == null
        }
        ruleMatch.domainExact?.let { exact ->
            if (hostname.equals(exact, ignoreCase = true)) {
                return true
            }
        }
        ruleMatch.domainWildcard?.removePrefix("*.")?.let { suffix ->
            if (hostname.equals(suffix, ignoreCase = true) || hostname.endsWith(".$suffix", ignoreCase = true)) {
                return true
            }
        }
        return ruleMatch.domainExact == null && ruleMatch.domainWildcard == null
    }

    private fun cidrMatches(cidr: String?, dstIp: String): Boolean {
        if (cidr.isNullOrBlank()) {
            return true
        }
        return runCatching {
            val parts = cidr.split('/')
            if (parts.size != 2) {
                return false
            }
            val network = InetAddress.getByName(parts[0]).address
            val target = InetAddress.getByName(dstIp).address
            val prefix = parts[1].toInt()
            if (network.size != target.size) {
                return false
            }
            val totalBits = network.size * 8
            if (prefix !in 0..totalBits) {
                return false
            }
            val mask = if (prefix == 0) {
                BigInteger.ZERO
            } else {
                BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE)
                    .shiftRight(totalBits - prefix)
                    .shiftLeft(totalBits - prefix)
            }
            val networkValue = BigInteger(1, network)
            val targetValue = BigInteger(1, target)
            networkValue.and(mask) == targetValue.and(mask)
        }.getOrDefault(false)
    }

    private fun portMatches(range: PortRangeDocument?, dstPort: Int): Boolean =
        range == null || dstPort in range.start..range.end

    private fun downgradePreset(
        engine: LoadedEngine,
        profile: ProfileDocument,
        ruleId: String,
        nowMs: Long,
    ): Preset {
        val timestamps = engine.failures[ruleId].orEmpty()
        val cutoff = nowMs - profile.fallbackPolicy.timeWindowSeconds * 1000
        val recentFailures = timestamps.count { it >= cutoff }
        return when (profile.preset) {
            Preset.Aggressive ->
                when {
                    recentFailures >= profile.fallbackPolicy.aggressiveThreshold + profile.fallbackPolicy.balancedThreshold ->
                        Preset.Compatible
                    recentFailures >= profile.fallbackPolicy.aggressiveThreshold -> Preset.Balanced
                    else -> Preset.Aggressive
                }

            Preset.Balanced ->
                if (recentFailures >= profile.fallbackPolicy.balancedThreshold) {
                    Preset.Compatible
                } else {
                    Preset.Balanced
                }

            else -> profile.preset
        }
    }

    private fun buildSplitPlan(
        profile: ProfileDocument,
        preset: Preset,
        trafficKind: TrafficKind,
        bufferedLength: Int,
    ): SplitPlan? {
        if (bufferedLength <= 0) {
            return null
        }
        val offsets = when (preset) {
            Preset.Compatible ->
                when (trafficKind) {
                    TrafficKind.Http -> mutableListOf(8, 32)
                    TrafficKind.Tls -> mutableListOf(5, 43)
                    TrafficKind.Unknown -> mutableListOf(16)
                }

            Preset.Balanced ->
                when (trafficKind) {
                    TrafficKind.Http -> mutableListOf(8, 24, 64)
                    TrafficKind.Tls -> mutableListOf(5, 43, 128)
                    TrafficKind.Unknown -> mutableListOf(16, 48)
                }

            Preset.Aggressive ->
                when (trafficKind) {
                    TrafficKind.Http -> mutableListOf(4, 12, 32, 96)
                    TrafficKind.Tls -> mutableListOf(5, 24, 64, 160)
                    TrafficKind.Unknown -> mutableListOf(8, 24, 64)
                }

            Preset.Custom ->
                profile.customTechniques?.let { custom ->
                    when (trafficKind) {
                        TrafficKind.Http -> custom.httpSplitOffsets.toMutableList()
                        TrafficKind.Tls -> custom.tlsSplitOffsets.toMutableList()
                        TrafficKind.Unknown -> custom.httpSplitOffsets.toMutableList()
                    }
                } ?: mutableListOf()
        }
        val filteredOffsets = offsets
            .filter { it > 0 && it < bufferedLength }
            .distinct()
            .sorted()
        if (filteredOffsets.isEmpty()) {
            return null
        }
        return SplitPlan(
            strategy = "${preset.name.lowercase()}-${trafficKind.name.lowercase()}",
            offsets = filteredOffsets,
        )
    }

    private fun extractTarget(bytes: ByteArray, flags: FeatureFlagsDocument): HostExtraction {
        if (flags.enableHttpHost && looksLikeHttp(bytes)) {
            return extractHttpHost(bytes)
        }
        if (flags.enableTlsSni && looksLikeTls(bytes)) {
            return extractTlsSni(bytes)
        }
        return HostExtraction(
            hostname = null,
            needsMoreData = bytes.size < 32,
            trafficKind = TrafficKind.Unknown,
        )
    }

    private fun looksLikeHttp(bytes: ByteArray): Boolean {
        val methods = listOf("GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "CONNECT ")
        val prefix = bytes.decodeToString()
        return methods.any { prefix.startsWith(it) }
    }

    private fun extractHttpHost(bytes: ByteArray): HostExtraction {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd < 0) {
            return HostExtraction(needsMoreData = true, trafficKind = TrafficKind.Http)
        }
        val hostname = text.substring(0, headerEnd)
            .lineSequence()
            .firstOrNull { line -> line.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        return HostExtraction(
            hostname = hostname,
            needsMoreData = false,
            trafficKind = TrafficKind.Http,
        )
    }

    private fun looksLikeTls(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes[0].toInt() == 0x16 && bytes[1].toInt() == 0x03

    private fun extractTlsSni(bytes: ByteArray): HostExtraction {
        if (bytes.size < 5) {
            return HostExtraction(needsMoreData = true, trafficKind = TrafficKind.Tls)
        }
        val recordLength = u16(bytes[3], bytes[4])
        if (bytes.size < 5 + recordLength) {
            return HostExtraction(needsMoreData = true, trafficKind = TrafficKind.Tls)
        }

        var cursor = 5
        if (bytes.getOrNull(cursor)?.toInt() != 0x01) {
            return HostExtraction(needsMoreData = false, trafficKind = TrafficKind.Tls)
        }
        cursor += 4
        cursor += 2
        cursor += 32

        val sessionLength = bytes.getOrNull(cursor)?.unsigned() ?: return tlsNeedMore()
        cursor += 1 + sessionLength

        val cipherLength = bytes.readU16(cursor) ?: return tlsNeedMore()
        cursor += 2 + cipherLength

        val compressionLength = bytes.getOrNull(cursor)?.unsigned() ?: return tlsNeedMore()
        cursor += 1 + compressionLength

        val extensionsLength = bytes.readU16(cursor) ?: return tlsNeedMore()
        cursor += 2
        val extensionEnd = cursor + extensionsLength
        if (extensionEnd > bytes.size) {
            return tlsNeedMore()
        }

        while (cursor + 4 <= extensionEnd) {
            val extensionType = bytes.readU16(cursor) ?: return tlsNeedMore()
            val extensionSize = bytes.readU16(cursor + 2) ?: return tlsNeedMore()
            cursor += 4
            val currentExtensionEnd = cursor + extensionSize
            if (currentExtensionEnd > extensionEnd) {
                return tlsNeedMore()
            }
            if (extensionType == 0x0000) {
                val listLength = bytes.readU16(cursor) ?: return tlsNeedMore()
                var nameCursor = cursor + 2
                val nameEnd = nameCursor + listLength
                if (nameEnd > currentExtensionEnd) {
                    return tlsNeedMore()
                }
                while (nameCursor + 3 <= nameEnd) {
                    val nameType = bytes[nameCursor].unsigned()
                    val nameLength = bytes.readU16(nameCursor + 1) ?: return tlsNeedMore()
                    nameCursor += 3
                    if (nameCursor + nameLength > nameEnd) {
                        return tlsNeedMore()
                    }
                    if (nameType == 0) {
                        val hostname = bytes.copyOfRange(nameCursor, nameCursor + nameLength)
                            .toString(StandardCharsets.UTF_8)
                        return HostExtraction(
                            hostname = hostname,
                            needsMoreData = false,
                            trafficKind = TrafficKind.Tls,
                        )
                    }
                    nameCursor += nameLength
                }
            }
            cursor = currentExtensionEnd
        }

        return HostExtraction(needsMoreData = false, trafficKind = TrafficKind.Tls)
    }

    private fun tlsNeedMore(): HostExtraction =
        HostExtraction(needsMoreData = true, trafficKind = TrafficKind.Tls)

    private fun activeProfile(config: PrototypeConfigDocument): ProfileDocument =
        config.profiles.first { it.id == config.activeProfileId }

    private fun isSemverLike(value: String): Boolean =
        Regex("""^\d+\.\d+\.\d+([-.][A-Za-z0-9.]+)?$""").matches(value)

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun u16(first: Byte, second: Byte): Int = (first.unsigned() shl 8) or second.unsigned()

    private fun ByteArray.readU16(offset: Int): Int? {
        val first = getOrNull(offset) ?: return null
        val second = getOrNull(offset + 1) ?: return null
        return u16(first, second)
    }

    companion object {
        private val nextHandle = AtomicLong(1)
        private val engines = ConcurrentHashMap<Long, LoadedEngine>()
    }
}

