package com.owlmedia.racecontrol.di

import android.content.Context
import com.owlmedia.racecontrol.BuildConfig
import com.owlmedia.racecontrol.data.remote.AuthInterceptor
import com.owlmedia.racecontrol.data.remote.BaseUrlInterceptor
import com.owlmedia.racecontrol.data.remote.CompositeTokenProvider
import com.owlmedia.racecontrol.data.remote.RaceControlApi
import com.owlmedia.racecontrol.data.remote.TokenProvider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Lives for the process. Used only for work that must outlive any one
     * screen: mirroring settings into the network layer, for example.
     */
    @Provides
    @Singleton
    fun provideApplicationScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // FastF1 adds fields between versions; an unknown key must never be
        // fatal on a client that ships separately from the backend.
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache =
        Cache(context.cacheDir.resolve("http_cache"), 10L * 1024 * 1024)

    @Provides
    @Singleton
    fun provideOkHttp(
        cache: Cache,
        baseUrlInterceptor: BaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        // Loading a session with telemetry is pandas-heavy on the backend and
        // the first request for a race downloads and caches it, so these are
        // generous on purpose - matching the iOS URLSession configuration.
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        // BASIC only: HEADERS would print the bearer token.
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Placeholder: BaseUrlInterceptor rewrites scheme/host/port per request
        // from the user's configured server address.
        .baseUrl("http://localhost:8000/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): RaceControlApi = retrofit.create(RaceControlApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    /**
     * Manual token (Settings) first, Play Integrity otherwise; see
     * [CompositeTokenProvider]. `StaticTokenProvider` and
     * `PlayIntegrityTokenProvider` are constructor-injected directly by
     * `CompositeTokenProvider`, so no separate binding is needed for either.
     */
    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: CompositeTokenProvider): TokenProvider
}
