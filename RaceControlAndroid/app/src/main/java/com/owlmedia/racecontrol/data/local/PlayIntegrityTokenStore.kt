package com.owlmedia.racecontrol.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the short-lived JWT that backend Play Integrity verification mints,
 * so `PlayIntegrityTokenProvider` only pays for the full nonce -> Play
 * Services -> verify round trip when the cached token is missing or close to
 * expiry, both to stay well under Play Integrity's per-app request quota,
 * and because that round trip takes a couple of seconds real requests
 * shouldn't have to wait on.
 *
 * Same storage mechanism as [SecureTokenStore] (Android Keystore-backed,
 * excluded from backup, see res/xml/backup_rules.xml) but a separate file:
 * this token is minted by the app itself, not entered by the user, and
 * carries an expiry the manual token doesn't have.
 */
@Singleton
class PlayIntegrityTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val FILE = "racecontrol_play_integrity_prefs"
        const val KEY_TOKEN = "jwt"
        const val KEY_EXPIRES_AT = "expires_at_epoch_seconds"
        const val TAG = "PlayIntegrityTokenStore"

        // Refresh a little before the server-declared expiry so a request in
        // flight doesn't race a token that expires mid-call.
        const val EXPIRY_MARGIN_SECONDS = 60L
    }

    private val prefs: SharedPreferences by lazy {
        try {
            create()
        } catch (e: Exception) {
            // A corrupted keystore entry (an OEM bug, or a restored backup)
            // would otherwise crash on every launch. Drop the file and start
            // clean: the next call just re-runs the full verification flow.
            Log.w(TAG, "Encrypted prefs unavailable, resetting", e)
            context.deleteSharedPreferences(FILE)
            create()
        }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Read synchronously; used by [PlayIntegrityTokenProvider] on a background thread. */
    fun currentToken(): String = prefs.getString(KEY_TOKEN, "").orEmpty()

    fun isExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val nowSeconds = System.currentTimeMillis() / 1000
        return nowSeconds >= (expiresAt - EXPIRY_MARGIN_SECONDS)
    }

    fun save(jwt: String, expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() / 1000 + expiresInSeconds
        prefs.edit()
            .putString(KEY_TOKEN, jwt)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
