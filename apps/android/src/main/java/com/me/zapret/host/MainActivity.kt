package com.me.zapret.host

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.me.zapret.android.service.ZapretVpnService

class MainActivity : AppCompatActivity() {
    private var pendingStart = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingStart) {
            startLibraryService()
        } else if (pendingStart) {
            toast("VPN permission was not granted")
        }
        pendingStart = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "zapret Host"
        setContentView(buildContent())
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

        container.addView(header("host app works"))
        container.addView(body("This module is a launchable Android host for :platform:android."))
        container.addView(body("It can request VPN permission and start/stop ZapretVpnService for manual debugging."))
        container.addView(body("Service action source: ${ZapretVpnService.ACTION_START}"))
        container.addView(actionButton("Request VPN permission") { requestVpnPermission() })
        container.addView(actionButton("Start library service") { prepareAndStartService() })
        container.addView(actionButton("Stop library service") { stopLibraryService() })

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

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            toast("VPN permission already granted")
            return
        }
        vpnPermissionLauncher.launch(intent)
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
            putExtra(ZapretVpnService.EXTRA_CONFIG_JSON, SAMPLE_CONFIG_JSON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
        toast("Host app requested ZapretVpnService start")
    }

    private fun stopLibraryService() {
        val serviceIntent = Intent(this, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_STOP
        }
        startService(serviceIntent)
        toast("Host app requested ZapretVpnService stop")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val SAMPLE_CONFIG_JSON = """
            {
              "app_version":"0.1.0",
              "core_version":"0.1.0",
              "config_schema_version":1,
              "feature_flags":{
                "enable_tls_sni":true,
                "enable_http_host":true,
                "enable_aggressive_split":true,
                "enable_self_test_ab":true
              },
              "active_profile_id":"default",
              "profiles":[
                {
                  "id":"default",
                  "name":"Default",
                  "preset":"Balanced",
                  "custom_techniques":null,
                  "rules":[],
                  "quic_policy":"Allow",
                  "fallback_policy":{
                    "aggressive_threshold":3,
                    "balanced_threshold":2,
                    "time_window_seconds":300
                  }
                }
              ],
              "settings":{
                "language":"EN",
                "theme_mode":"SYSTEM",
                "debug_logs":false,
                "safe_mode_enabled":true,
                "updates":{
                  "repo_reference":"owner/repo",
                  "api_base_url":"https://api.github.com",
                  "auto_update":true,
                  "wifi_only":true,
                  "apply_on_next_vpn_start":true
                }
              },
              "last_valid_config_snapshot":null
            }
        """
    }
}
