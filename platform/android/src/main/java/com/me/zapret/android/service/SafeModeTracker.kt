package com.me.zapret.android.service

import android.content.Context
import android.content.SharedPreferences

class SafeModeTracker(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordFailure(nowMs: Long = System.currentTimeMillis()): Boolean {
        val timestamps = readTimestamps()
            .filter { nowMs - it <= WINDOW_MS }
            .toMutableList()
        timestamps += nowMs
        writeTimestamps(timestamps)
        return timestamps.size >= FAILURE_THRESHOLD
    }

    fun clear() {
        preferences.edit().remove(KEY_TIMESTAMPS).apply()
    }

    fun currentFailureCount(nowMs: Long = System.currentTimeMillis()): Int =
        readTimestamps().count { nowMs - it <= WINDOW_MS }

    private fun readTimestamps(): List<Long> =
        preferences.getString(KEY_TIMESTAMPS, "")
            .orEmpty()
            .split(',')
            .mapNotNull { token -> token.toLongOrNull() }

    private fun writeTimestamps(timestamps: List<Long>) {
        preferences.edit().putString(KEY_TIMESTAMPS, timestamps.joinToString(",")).apply()
    }

    companion object {
        private const val PREFS_NAME = "zapret_safe_mode"
        private const val KEY_TIMESTAMPS = "crash_timestamps"
        private const val WINDOW_MS = 5 * 60 * 1000L
        private const val FAILURE_THRESHOLD = 3
    }
}

