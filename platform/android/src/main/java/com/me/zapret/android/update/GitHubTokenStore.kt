package com.me.zapret.android.update

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GitHubTokenStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(token: String?) {
        preferences.edit().apply {
            if (token.isNullOrBlank()) {
                remove(KEY_TOKEN)
            } else {
                putString(KEY_TOKEN, token)
            }
        }.apply()
    }

    fun load(): String? = preferences.getString(KEY_TOKEN, null)

    companion object {
        private const val FILE_NAME = "zapret_github_tokens"
        private const val KEY_TOKEN = "pat"
    }
}

