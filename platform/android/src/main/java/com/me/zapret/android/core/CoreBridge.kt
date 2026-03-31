package com.me.zapret.android.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface NativeBindings {
    fun getCoreVersionJson(): String
    fun getSchemaVersion(): Int
    fun validateConfig(configJson: String): String
    fun loadConfig(configJson: String): String
    fun inspectEarlyBytes(
        engineHandle: Long,
        flowKeyJson: String,
        bufferedBytes: ByteArray,
        nowMs: Long,
    ): String

    fun reportFlowResult(engineHandle: Long, reportJson: String): Boolean
    fun healthcheck(configJson: String): String
    fun freeEngine(engineHandle: Long): Boolean
}

class NoopNativeBindings : NativeBindings {
    override fun getCoreVersionJson(): String = """{"coreVersion":"unbound"}"""

    override fun getSchemaVersion(): Int = 1

    override fun validateConfig(configJson: String): String = """{"ok":false,"error":"native bindings not attached"}"""

    override fun loadConfig(configJson: String): String = """{"ok":false,"error":"native bindings not attached"}"""

    override fun inspectEarlyBytes(
        engineHandle: Long,
        flowKeyJson: String,
        bufferedBytes: ByteArray,
        nowMs: Long,
    ): String = """{"state":"FINAL","action":"DIRECT","error":"native bindings not attached"}"""

    override fun reportFlowResult(engineHandle: Long, reportJson: String): Boolean = false

    override fun healthcheck(configJson: String): String = """{"ok":false,"error":"native bindings not attached"}"""

    override fun freeEngine(engineHandle: Long): Boolean = false
}

class CoreBridge(
    private val bindings: NativeBindings = NoopNativeBindings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun validateConfig(configJson: String): NativeResponse =
        json.decodeFromString(bindings.validateConfig(configJson))

    fun loadConfig(configJson: String): NativeResponse =
        json.decodeFromString(bindings.loadConfig(configJson))

    fun inspectEarlyBytes(
        engineHandle: Long,
        flowKey: FlowKey,
        bufferedBytes: ByteArray,
        nowMs: Long,
    ): EarlyDecision = json.decodeFromString(
        bindings.inspectEarlyBytes(
            engineHandle = engineHandle,
            flowKeyJson = json.encodeToString(flowKey),
            bufferedBytes = bufferedBytes,
            nowMs = nowMs,
        ),
    )

    fun reportFlowResult(engineHandle: Long, report: FlowResultReport): Boolean =
        bindings.reportFlowResult(engineHandle, json.encodeToString(report))

    fun healthcheckConfig(configJson: String): NativeResponse =
        json.decodeFromString(bindings.healthcheck(configJson))

    fun getSchemaVersion(): Int = bindings.getSchemaVersion()

    fun freeEngine(engineHandle: Long): Boolean = bindings.freeEngine(engineHandle)
}
