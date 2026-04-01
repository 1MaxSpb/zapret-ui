package com.me.zapret.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.me.zapret.android.R
import com.me.zapret.android.core.CoreBridge
import com.me.zapret.android.core.LocalNativeBindings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream

class ZapretVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val safeModeTracker by lazy { SafeModeTracker(this) }
    private val runtimeStateStore by lazy { ServiceRuntimeStateStore(this) }
    private val tcpFlowCoordinator by lazy { TcpFlowCoordinator(createCoreBridge()) }
    private val udpDirectForwarder = UdpDirectForwarder()

    private var vpnInterface: ParcelFileDescriptor? = null
    private var engineHandle: Long? = null
    private var activeBridge: CoreBridge? = null
    private var isStopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn(intent.getStringExtra(EXTRA_CONFIG_JSON).orEmpty())
            ACTION_STOP -> stopVpn()
            ACTION_RUN_SELF_TEST -> runProtectedSelfTest()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (!isStopping) {
            releaseResources()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    protected fun createCoreBridge(): CoreBridge = CoreBridge(LocalNativeBindings())

    private fun startVpn(configJson: String) {
        if (vpnInterface != null && engineHandle != null) {
            runtimeStateStore.update(
                status = PrototypeServiceStatus.ON,
                activeProfileId = runtimeStateStore.read().activeProfileId,
                lastError = null,
            )
            return
        }
        runtimeStateStore.update(PrototypeServiceStatus.STARTING)
        if (configJson.isBlank()) {
            stopVpn(
                status = PrototypeServiceStatus.ERROR,
                lastError = "Config payload is empty",
            )
            return
        }

        val bridge = createCoreBridge()
        activeBridge = bridge
        val validation = bridge.validateConfig(configJson)
        if (!validation.ok) {
            stopVpn(
                status = PrototypeServiceStatus.ERROR,
                activeProfileId = validation.activeProfileId,
                lastError = validation.error ?: "Config validation failed",
            )
            return
        }

        val loadResult = bridge.loadConfig(configJson)
        val handle = loadResult.engineHandle ?: run {
            stopVpn(
                status = PrototypeServiceStatus.ERROR,
                activeProfileId = validation.activeProfileId,
                lastError = loadResult.error ?: "Engine load failed",
            )
            return
        }
        engineHandle = handle

        startForeground(NOTIFICATION_ID, buildNotification())
        val builder = Builder()
            .setSession("zapret-ui prototype")
            .setMtu(1500)
            .addAddress("10.222.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
        try {
            builder.addAllowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            stopVpn(
                status = PrototypeServiceStatus.ERROR,
                activeProfileId = validation.activeProfileId,
                lastError = "Failed to scope prototype VPN to host application",
            )
            return
        }

        vpnInterface = builder.establish()
        val tunnel = vpnInterface
        if (tunnel == null) {
            stopVpn(
                status = PrototypeServiceStatus.ERROR,
                activeProfileId = validation.activeProfileId,
                lastError = "VpnService.Builder.establish() returned null",
            )
            return
        }
        runtimeStateStore.update(
            PrototypeServiceStatus.ON,
            activeProfileId = validation.activeProfileId,
        )
        serviceScope.launch {
            runTunLoop(tunnel)
        }
    }

    private fun stopVpn(
        status: PrototypeServiceStatus = PrototypeServiceStatus.OFF,
        activeProfileId: String? = null,
        lastError: String? = null,
    ) {
        isStopping = true
        releaseResources()
        runtimeStateStore.update(
            status = status,
            activeProfileId = activeProfileId,
            lastError = lastError,
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isStopping = false
    }

    private fun runProtectedSelfTest() {
        val bridge = activeBridge
        val handle = engineHandle
        if (bridge == null || handle == null) {
            runtimeStateStore.recordSelfTest(
                "Self-test skipped: prototype VPN is not running",
            )
            return
        }

        serviceScope.launch {
            val summary = runCatching {
                val result = ProtectedSelfTestRunner(this@ZapretVpnService, bridge)
                    .runApplyProbe(
                        engineHandle = handle,
                        probe = SelfTestProbe(host = "example.com"),
                    )
                buildString {
                    append("target=${result.target}")
                    append(", status=${result.status}")
                    append(", latency=${result.latencyMs}ms")
                    append(", rule=${result.matchedRuleId ?: "n/a"}")
                    append(", preset=${result.effectivePreset ?: "n/a"}")
                }
            }.getOrElse { error ->
                "Self-test failed: ${error.message ?: "unknown error"}"
            }
            runtimeStateStore.recordSelfTest(summary)
        }
    }

    private fun releaseResources() {
        engineHandle?.let { handle ->
            activeBridge?.freeEngine(handle)
        }
        vpnInterface?.close()
        vpnInterface = null
        engineHandle = null
        activeBridge = null
    }

    private fun runTunLoop(tunnel: ParcelFileDescriptor) {
        val input = FileInputStream(tunnel.fileDescriptor)
        val buffer = ByteArray(16 * 1024)
        try {
            while (!isStopping && vpnInterface != null) {
                val read = input.read(buffer)
                if (read <= 0) {
                    continue
                }
                // Prototype mode keeps the VPN limited to the host app package and relies on
                // protected sockets for self-test traffic. Captured packets are intentionally
                // dropped until the full forwarding dataplane is implemented.
            }
        } catch (_: Throwable) {
            if (isStopping || vpnInterface == null) {
                return
            }
            val enteredSafeMode = safeModeTracker.recordFailure()
            stopVpn(
                status = if (enteredSafeMode) PrototypeServiceStatus.SAFE_MODE else PrototypeServiceStatus.ERROR,
                lastError = "Tun loop terminated unexpectedly",
            )
        } finally {
            input.close()
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.zapret_service_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.zapret_service_notification_title))
            .setContentText(getString(R.string.zapret_service_notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.me.zapret.android.START"
        const val ACTION_STOP = "com.me.zapret.android.STOP"
        const val ACTION_RUN_SELF_TEST = "com.me.zapret.android.RUN_SELF_TEST"
        const val EXTRA_CONFIG_JSON = "config_json"
        private const val CHANNEL_ID = "zapret_vpn_service"
        private const val NOTIFICATION_ID = 1001
    }
}
