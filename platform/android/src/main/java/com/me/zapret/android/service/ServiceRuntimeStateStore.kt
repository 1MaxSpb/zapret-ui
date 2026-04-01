package com.me.zapret.android.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

enum class PrototypeServiceStatus {
    OFF,
    STARTING,
    ON,
    ERROR,
    SAFE_MODE,
}

data class ServiceRuntimeSnapshot(
    val status: PrototypeServiceStatus,
    val activeProfileId: String?,
    val lastError: String?,
    val lastSelfTestSummary: String?,
    val lastSelfTestAtMs: Long,
    val updatedAtMs: Long,
)

class ServiceRuntimeStateStore(
    private val context: Context,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): ServiceRuntimeSnapshot =
        ServiceRuntimeSnapshot(
            status = PrototypeServiceStatus.valueOf(
                preferences.getString(KEY_STATUS, PrototypeServiceStatus.OFF.name)
                    ?: PrototypeServiceStatus.OFF.name,
            ),
            activeProfileId = preferences.getString(KEY_ACTIVE_PROFILE_ID, null),
            lastError = preferences.getString(KEY_LAST_ERROR, null),
            lastSelfTestSummary = preferences.getString(KEY_LAST_SELF_TEST_SUMMARY, null),
            lastSelfTestAtMs = preferences.getLong(KEY_LAST_SELF_TEST_AT_MS, 0L),
            updatedAtMs = preferences.getLong(KEY_UPDATED_AT_MS, 0L),
        )

    fun update(
        status: PrototypeServiceStatus,
        activeProfileId: String? = null,
        lastError: String? = null,
    ) {
        preferences.edit().apply {
            putString(KEY_STATUS, status.name)
            putString(KEY_ACTIVE_PROFILE_ID, activeProfileId)
            putString(KEY_LAST_ERROR, lastError)
            putLong(KEY_UPDATED_AT_MS, System.currentTimeMillis())
        }.apply()

        context.sendBroadcast(
            Intent(ACTION_STATE_CHANGED).setPackage(context.packageName),
        )
    }

    fun recordSelfTest(summary: String) {
        preferences.edit().apply {
            putString(KEY_LAST_SELF_TEST_SUMMARY, summary)
            putLong(KEY_LAST_SELF_TEST_AT_MS, System.currentTimeMillis())
            putLong(KEY_UPDATED_AT_MS, System.currentTimeMillis())
        }.apply()

        context.sendBroadcast(
            Intent(ACTION_STATE_CHANGED).setPackage(context.packageName),
        )
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.me.zapret.android.STATE_CHANGED"

        private const val PREFS_NAME = "zapret_service_state"
        private const val KEY_STATUS = "status"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_SELF_TEST_SUMMARY = "last_self_test_summary"
        private const val KEY_LAST_SELF_TEST_AT_MS = "last_self_test_at_ms"
        private const val KEY_UPDATED_AT_MS = "updated_at_ms"
    }
}
