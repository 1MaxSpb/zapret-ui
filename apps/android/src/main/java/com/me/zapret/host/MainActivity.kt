package com.me.zapret.host

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.me.zapret.android.core.PrototypeCoreFacade
import com.me.zapret.android.service.PrototypeServiceStatus
import com.me.zapret.android.service.ServiceRuntimeStateStore
import com.me.zapret.android.service.ZapretVpnService

class MainActivity : AppCompatActivity() {
    private val prototypeCore by lazy { PrototypeCoreFacade() }
    private val runtimeStateStore by lazy { ServiceRuntimeStateStore(this) }

    private lateinit var serviceStateView: TextView
    private lateinit var selfTestView: TextView
    private lateinit var coreStateView: TextView
    private lateinit var configStateView: TextView

    private var pendingStart = false
    private var receiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingStart) {
            startLibraryService()
        } else if (pendingStart) {
            toast("VPN permission was not granted")
            refreshState()
        }
        pendingStart = false
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            toast("Notification permission is recommended for the VPN foreground service")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "zapret Host Prototype"
        setContentView(buildContent())
        requestNotificationPermissionIfNeeded()
        renderCoreSummary("Prototype core ready")
        renderConfigSummary()
    }

    override fun onStart() {
        super.onStart()
        registerStateReceiver()
        refreshState()
    }

    override fun onStop() {
        unregisterStateReceiver()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildContent(): ScrollView {
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        serviceStateView = body("")
        selfTestView = body("")
        coreStateView = body("")
        configStateView = body("")

        container.addView(header("host app works"))
        container.addView(body("This is a runnable Android host for :platform:android."))
        container.addView(body("The prototype can request VPN permission, start the library service, run a protected self-test, and execute local core checks."))
        container.addView(serviceStateView)
        container.addView(selfTestView)
        container.addView(coreStateView)
        container.addView(configStateView)
        container.addView(actionButton("Request VPN permission") { requestVpnPermission() })
        container.addView(spacer())
        container.addView(actionButton("Start prototype VPN") { prepareAndStartService() })
        container.addView(spacer())
        container.addView(actionButton("Stop prototype VPN") { stopLibraryService() })
        container.addView(spacer())
        container.addView(actionButton("Run protected self-test") { runProtectedSelfTest() })
        container.addView(spacer())
        container.addView(actionButton("Validate sample config") { validateSampleConfig() })
        container.addView(spacer())
        container.addView(actionButton("Run sample core decision") { inspectSampleRequest() })
        container.addView(spacer())
        container.addView(actionButton("Refresh status") { refreshState() })

        return ScrollView(this).apply {
            addView(container)
        }
    }

    private fun header(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 22f
            setPadding(0, 0, 0, 24)
        }

    private fun body(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

    private fun actionButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun spacer(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (10 * resources.displayMetrics.density).toInt(),
            )
        }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            toast("VPN permission already granted")
            refreshState()
            return
        }
        vpnPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun prepareAndStartService() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingStart = true
            vpnPermissionLauncher.launch(intent)
            return
        }
        startLibraryService()
    }

    private fun startLibraryService() {
        val serviceIntent = Intent(this, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_START
            putExtra(ZapretVpnService.EXTRA_CONFIG_JSON, prototypeCore.sampleConfigJson())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
        toast("Prototype VPN start requested")
        scheduleRefresh()
    }

    private fun stopLibraryService() {
        val serviceIntent = Intent(this, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_STOP
        }
        startService(serviceIntent)
        toast("Prototype VPN stop requested")
        scheduleRefresh()
    }

    private fun runProtectedSelfTest() {
        val snapshot = runtimeStateStore.read()
        if (snapshot.status != PrototypeServiceStatus.ON) {
            toast("Start the prototype VPN before running self-test")
            refreshState()
            return
        }

        val serviceIntent = Intent(this, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_RUN_SELF_TEST
        }
        startService(serviceIntent)
        toast("Protected self-test requested")
        scheduleRefresh()
    }

    private fun validateSampleConfig() {
        val response = prototypeCore.validateSampleConfig()
        renderCoreSummary(
            if (response.ok) {
                "Config validation: OK, activeProfile=${response.activeProfileId}, schema=${response.schemaVersion}"
            } else {
                "Config validation failed: ${response.error}"
            },
        )
    }

    private fun inspectSampleRequest() {
        val decision = prototypeCore.inspectSampleHttp()
        renderCoreSummary(
            "Sample decision: action=${decision.action}, preset=${decision.effectivePreset}, rule=${decision.matchedRuleId}, host=${decision.hostname}, error=${decision.error ?: "none"}",
        )
    }

    private fun refreshState() {
        val snapshot = runtimeStateStore.read()
        serviceStateView.text =
            "Service status: ${snapshot.status} | profile=${snapshot.activeProfileId ?: "n/a"} | updated=${snapshot.updatedAtMs}"
        if (!snapshot.lastError.isNullOrBlank()) {
            serviceStateView.append("\nLast error: ${snapshot.lastError}")
        }

        selfTestView.text =
            if (snapshot.lastSelfTestSummary.isNullOrBlank()) {
                "Self-test: not run yet"
            } else {
                "Self-test: ${snapshot.lastSelfTestSummary}\nUpdated: ${snapshot.lastSelfTestAtMs}"
            }

        renderConfigSummary()
    }

    private fun renderCoreSummary(text: String) {
        coreStateView.text = text
    }

    private fun renderConfigSummary() {
        val healthcheck = prototypeCore.healthcheckSampleConfig()
        configStateView.text =
            if (healthcheck.ok) {
                "Healthcheck: OK, sample config is accepted by the local core engine."
            } else {
                "Healthcheck failed: ${healthcheck.error}"
            }
    }

    private fun scheduleRefresh() {
        window.decorView.postDelayed({ refreshState() }, 400L)
    }

    private fun registerStateReceiver() {
        if (receiverRegistered) {
            return
        }
        val filter = IntentFilter(ServiceRuntimeStateStore.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterStateReceiver() {
        if (!receiverRegistered) {
            return
        }
        unregisterReceiver(stateReceiver)
        receiverRegistered = false
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
