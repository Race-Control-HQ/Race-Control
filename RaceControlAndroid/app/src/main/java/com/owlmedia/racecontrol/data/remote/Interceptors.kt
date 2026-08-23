package com.owlmedia.racecontrol.data.remote

import com.owlmedia.racecontrol.data.local.SecureTokenStore
import com.owlmedia.racecontrol.data.local.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Points every request at whatever backend the user has configured.
 *
 * Retrofit fixes its base URL at build time, but this app's server address is a
 * setting that changes at runtime (the iOS app re-reads it from UserDefaults on
 * every call). Rewriting the host here keeps a single Retrofit instance while
 * still honouring a changed address immediately.
 *
 * The value is mirrored into a plain field because interceptors run on OkHttp's
 * thread pool and cannot suspend to read DataStore.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    settings: SettingsDataStore,
    scope: CoroutineScope,
) : Interceptor {

    @Volatile
    private var baseUrl: HttpUrl? = SettingsDataStore.DEFAULT_BASE_URL.toHttpUrlOrNull()

    init {
        scope.launch {
            settings.settings.collectLatest { current ->
                baseUrl = normalise(current.baseUrl)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val target = baseUrl ?: return chain.proceed(chain.request())
        val original = chain.request()

        val rebuilt = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()

        return chain.proceed(original.newBuilder().url(rebuilt).build())
    }

    private fun normalise(raw: String): HttpUrl? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        // Tolerate a bare "192.168.1.20:8000" the way a user would type it.
        val withScheme =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "http://$trimmed"
        return withScheme.toHttpUrlOrNull()
    }
}

/**
 * Adds the bearer token when one is configured.
 *
 * The token comes from [TokenProvider], [CompositeTokenProvider] in
 * practice, which prefers a manual Settings token and otherwise falls back to
 * a Play Integrity-verified JWT. Requests with neither configured go out
 * unauthenticated, which is what a local `./run.sh` backend with no auth
 * configured expects.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.token()
        val response = chain.proceed(applyToken(request, token))

        // A 401 usually means either the manual token changed since this
        // request was built, or a cached Play Integrity JWT was rejected (the
        // backend restarted with a new JWT_SECRET, say). Invalidate whatever
        // is cached and ask for a fresh token once before giving up - mirrors
        // the iOS single retry against a freshly-minted App Attest assertion.
        if (response.code == 401 && token.isNotEmpty()) {
            tokenProvider.invalidate()
            val refreshed = tokenProvider.token()
            if (refreshed.isNotEmpty() && refreshed != token) {
                response.close()
                return chain.proceed(applyToken(request, refreshed))
            }
        }
        return response
    }

    private fun applyToken(request: Request, token: String): Request =
        if (token.isEmpty()) {
            request
        } else {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        }
}

/**
 * Where the bearer token comes from.
 *
 * [CompositeTokenProvider] is what's actually bound to this interface (see
 * AppModule): it prefers a manually-entered token when one is set, and
 * otherwise falls back to Play Integrity, mirroring how the iOS app prefers
 * an admin token and otherwise falls back to an App Attest assertion.
 */
interface TokenProvider {
    fun token(): String

    /**
     * Drop any cached credential so the next [token] call fetches a fresh
     * one. A no-op for a provider with nothing to cache (a manual token isn't
     * "refreshed", the user just edits it in Settings).
     */
    fun invalidate() {}
}

@Singleton
class StaticTokenProvider @Inject constructor(
    private val tokenStore: SecureTokenStore,
) : TokenProvider {
    override fun token(): String = tokenStore.currentToken()
}

/**
 * Manual token first (an admin/dev override entered in Settings, or a local
 * `./run.sh` backend that isn't gated at all), Play Integrity otherwise. This
 * is the Android counterpart of the iOS precedence: prefer the admin token
 * when one is configured, fall back to device attestation.
 */
@Singleton
class CompositeTokenProvider @Inject constructor(
    private val staticTokenProvider: StaticTokenProvider,
    private val playIntegrityTokenProvider: PlayIntegrityTokenProvider,
) : TokenProvider {
    override fun token(): String {
        val manual = staticTokenProvider.token()
        if (manual.isNotEmpty()) return manual
        return playIntegrityTokenProvider.token()
    }

    // A manual token has nothing to invalidate; only the auto-fetched Play
    // Integrity JWT can go stale between requests.
    override fun invalidate() = playIntegrityTokenProvider.invalidate()
}
