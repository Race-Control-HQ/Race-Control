package com.owlmedia.racecontrol.data.remote

import android.content.Context
import android.util.Log
import com.owlmedia.racecontrol.BuildConfig
import com.owlmedia.racecontrol.data.local.PlayIntegrityTokenStore
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Android counterpart of the iOS App Attest flow (backend/playintegrity.py is
 * the server side of this; backend/attest.py is what App Attest talks to).
 *
 * Google Play Integrity (not a static secret baked into the APK) vouches for
 * this being a genuine, unmodified copy of the app installed via Play on a
 * genuine device. A static shared token embedded in a publicly-distributed
 * APK is trivially extracted with a decompiler and is then just as good as no
 * auth at all; this has nothing for an attacker to extract, because the trust
 * comes from Google's attestation service, not from a value shipped in the app.
 *
 * Flow, each time the cached JWT is missing or close to expiry:
 *   1. GET  /playintegrity/challenge -> a one-time nonce
 *   2. Ask Play Services to vouch for us, binding the request to that nonce
 *   3. POST /playintegrity/verify with the resulting opaque token -> a
 *      short-lived JWT, which [PlayIntegrityTokenStore] caches
 *
 * [token] runs synchronously on OkHttp's interceptor thread (already off the
 * main thread, so blocking here is fine) and must be fast on the common path,
 * which is why the JWT is cached rather than re-verified on every request:
 * that would also blow through Play Integrity's per-app daily request quota.
 */
@Singleton
class PlayIntegrityTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PlayIntegrityTokenStore,
    baseUrlInterceptor: BaseUrlInterceptor,
) : TokenProvider {

    // Shares BaseUrlInterceptor with the main client (so this respects the
    // user's configured server address) but deliberately does NOT include
    // AuthInterceptor: that interceptor calls back into this class to get a
    // bearer token, so reusing a client with it attached would recurse.
    private val bootstrapClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun token(): String {
        cachedIfFresh()?.let { return it }
        return try {
            refresh()
        } catch (e: Exception) {
            // Network hiccup, Play Services unavailable, backend has Play
            // Integrity disabled, cloud project number not configured yet -
            // any of these should degrade to "no token" rather than crash the
            // request. AuthInterceptor sends the request unauthenticated,
            // which behaves exactly like a backend with no auth configured.
            Log.w(TAG, "Play Integrity token refresh failed", e)
            ""
        }
    }

    private fun cachedIfFresh(): String? {
        val cached = store.currentToken()
        return cached.takeIf { it.isNotEmpty() && !store.isExpired() }
    }

    override fun invalidate() {
        store.clear()
    }

    @Synchronized
    private fun refresh(): String {
        // Another thread may have refreshed while this one waited for the lock.
        cachedIfFresh()?.let { return it }

        check(BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER != 0L) {
            "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER is still the placeholder 0L - " +
                "set it to the real Google Cloud project number before release"
        }

        val nonce = fetchNonce()
        val integrityManager = IntegrityManagerFactory.create(context)
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .setCloudProjectNumber(BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER)
            .build()
        val response = Tasks.await(
            integrityManager.requestIntegrityToken(request), 15, TimeUnit.SECONDS,
        )
        val verified = verify(nonce, response.token())
        store.save(verified.token, verified.expiresIn)
        return verified.token
    }

    private fun fetchNonce(): String {
        val request = Request.Builder().url(bootstrapUrl("playintegrity/challenge")).get().build()
        bootstrapClient.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "GET /playintegrity/challenge -> HTTP ${resp.code}" }
            val body = resp.body?.string().orEmpty()
            return json.decodeFromString(ChallengeResponse.serializer(), body).nonce
        }
    }

    private fun verify(nonce: String, integrityToken: String): VerifyResponse {
        val payload = json.encodeToString(
            VerifyBody.serializer(),
            VerifyBody(nonce = nonce, integrityToken = integrityToken),
        )
        val request = Request.Builder()
            .url(bootstrapUrl("playintegrity/verify"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        bootstrapClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            check(resp.isSuccessful) { "POST /playintegrity/verify -> HTTP ${resp.code}: $body" }
            return json.decodeFromString(VerifyResponse.serializer(), body)
        }
    }

    /**
     * [BaseUrlInterceptor] rewrites scheme/host/port from the user's configured
     * server address; this placeholder only needs to parse as a valid URL with
     * the right path for that rewrite to happen (same trick as the placeholder
     * base URL in AppModule's Retrofit instance).
     */
    private fun bootstrapUrl(path: String): String = "http://localhost/$path"

    private companion object {
        const val TAG = "PlayIntegrityTokenProvider"
    }
}

@Serializable
private data class ChallengeResponse(val nonce: String)

@Serializable
private data class VerifyBody(val nonce: String, val integrityToken: String)

@Serializable
private data class VerifyResponse(val token: String, val expiresIn: Long)
