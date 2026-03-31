package com.me.zapret.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.me.zapret.android.R
import com.me.zapret.android.core.CoreBridge
import com.me.zapret.android.core.NoopNativeBindings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

class ZapretVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val safeModeTracker by lazy { SafeModeTracker(this) }
    private val tcpFlowCoordinator by lazy { TcpFlowCoordinator(createCoreBridge()) }
    private val udpDirectForwarder = UdpDirectForwarder()

    private var vpnInterface: ParcelFileDescriptor? = null
    private var engineHandle: Long? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn(intent.getStringExtra(EXTRA_CONFIG_JSON).orEmpty())
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    protected open fun createCoreBridge(): CoreBridge = CoreBridge(NoopNativeBindings())

    private fun startVpn(configJson: String) {
        if (configJson.isBlank()) {
            stopSelf()
            return
        }

        val bridge = createCoreBridge()
        val validation = bridge.validateConfig(configJson)
        if (!validation.ok) {
            stopSelf()
            return
        }

        val loadResult = bridge.loadConfig(configJson)
        val handle = loadResult.engineHandle ?: run {
            stopSelf()
            return
        }
        engineHandle = handle

        startForeground(NOTIFICATION_ID, buildNotification())
        vpnInterface = Builder()
            .setSession("zapret-ui")
            .addAddress("10.222.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .establish()

        val tunnel = vpnInterface ?: return
        serviceScope.launch {
            runTunLoop(bridge, handle, tunnel)
        }
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        engineHandle = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runTunLoop(
        bridge: CoreBridge,
        handle: Long,
        tunnel: ParcelFileDescriptor,
    ) {
        val input = FileInputStream(tunnel.fileDescriptor)
        val output = FileOutputStream(tunnel.fileDescriptor)
        val buffer = ByteArray(16 * 1024)
        try {
            while (vpnInterface != null) {
                val read = input.read(buffer)
                if (read <= 0) {
                    continue
                }
                // The MVP scaffold keeps transport ownership here and delegates only early-byte
                // inspection to Rust. Full TCP/UDP packetization is added on top of this loop.
                output.write(buffer, 0, read)
            }
        } catch (_: Throwable) {
            if (safeModeTracker.recordFailure()) {
                // The UI is expected to surface the safe mode banner based on this persisted state.
            }
            bridge.freeEngine(handle)
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
        const val EXTRA_CONFIG_JSON = "config_json"
        private const val CHANNEL_ID = "zapret_vpn_service"
        private const val NOTIFICATION_ID = 1001
    }
}
