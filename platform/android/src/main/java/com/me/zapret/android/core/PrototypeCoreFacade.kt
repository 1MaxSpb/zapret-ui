package com.me.zapret.android.core

class PrototypeCoreFacade(
    private val bridge: CoreBridge = CoreBridge(LocalNativeBindings()),
) {
    fun sampleConfigJson(): String = PrototypeConfigFactory.sampleConfigJson()

    fun validateSampleConfig(): NativeResponse =
        bridge.validateConfig(sampleConfigJson())

    fun healthcheckSampleConfig(): NativeResponse =
        bridge.healthcheckConfig(sampleConfigJson())

    fun inspectSampleHttp(): EarlyDecision {
        val loaded = bridge.loadConfig(sampleConfigJson())
        val handle = loaded.engineHandle
            ?: return EarlyDecision(error = loaded.error ?: "Failed to create engine")
        return try {
            bridge.inspectEarlyBytes(
                engineHandle = handle,
                flowKey = FlowKey(
                    proto = Protocol.TCP,
                    srcIp = "10.0.0.2",
                    srcPort = 54000,
                    dstIp = "93.184.216.34",
                    dstPort = 443,
                    direction = Direction.OUTBOUND,
                ),
                bufferedBytes = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".encodeToByteArray(),
                nowMs = System.currentTimeMillis(),
            )
        } finally {
            bridge.freeEngine(handle)
        }
    }
}
