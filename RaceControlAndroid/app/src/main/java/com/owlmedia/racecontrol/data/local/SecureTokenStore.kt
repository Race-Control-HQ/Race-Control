package com.owlmedia.racecontrol.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The API token, held in EncryptedSharedPreferences.
 *
 * This is the Android counterpart of the iOS Keychain store. The key material
 * lives in the Android Keystore, so the token is not readable from a backup or
 * from another app, and the file is excluded from cloud backup and device
 * transfer (see res/xml/backup_rules.xml).
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val FILE = "racecontrol_secure_prefs"
        const val KEY_TOKEN = "api_token"
        const val TAG = "SecureTokenStore"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // A corrupted keystore entry (an OEM bug, or a restored backup)
            // would otherwise crash on every launch. Drop the file and start
            // clean: the user re-enters a token, which beats an unusable app.
            Log.w(TAG, "Encrypted prefs unavailable, resetting", e)
            context.deleteSharedPreferences(FILE)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, "").orEmpty())
    val token: StateFlow<String> = _token.asStateFlow()

    /** Read synchronously; used by the OkHttp interceptor on a background thread. */
    fun currentToken(): String = _token.value

    fun setToken(value: String) {
        val trimmed = value.trim()
        prefs.edit().putString(KEY_TOKEN, trimmed).apply()
        _token.value = trimmed
    }
}
