package com.owlmedia.racecontrol

import androidx.test.core.app.ApplicationProvider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.owlmedia.racecontrol.data.remote.ApiException
import com.owlmedia.racecontrol.data.remote.RaceControlApi
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

/**
 * Contract tests for [RaceControlRepository] against a real Retrofit client
 * backed by [MockWebServer], mirroring the exact `Json`/converter
 * configuration `di/AppModule.kt` wires up in production (`ignoreUnknownKeys`,
 * `coerceInputValues`, `explicitNulls = false`) -- not a hand-mocked
 * `RaceControlApi`, so a real (de)serialization + Retrofit error-mapping bug
 * would actually be caught here.
 *
 * Robolectric supplies the `Context` `messageFor` needs for string resources;
 * there is no local database or Android-specific state under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13's newest supported SDK; project targetSdk is 35.
class RaceControlRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RaceControlRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(RaceControlApi::class.java)

        repository = RaceControlRepository(
            api = api,
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `driver standings success decodes the roster`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "position": 1, "points": 227, "wins": 7,
                    "driverId": "max_verstappen", "driverCode": "VER",
                    "givenName": "Max", "familyName": "Verstappen",
                    "teamName": "Red Bull Racing", "teamId": "red_bull"
                  }
                ]
                """.trimIndent(),
            ).setHeader("Content-Type", "application/json"),
        )

        val result = repository.driverStandings(2024)

        assertTrue(result.isSuccess)
        val standings = result.getOrThrow()
        assertEquals(1, standings.size)
        assertEquals("max_verstappen", standings.first().driverId)
        assertEquals("Max Verstappen", standings.first().fullName)
    }

    @Test
    fun `constructor standings success decodes the roster`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"position": 1, "points": 500, "wins": 12, "teamId": "red_bull", "teamName": "Red Bull Racing"}
                ]
                """.trimIndent(),
            ).setHeader("Content-Type", "application/json"),
        )

        val result = repository.constructorStandings(2024)

        assertTrue(result.isSuccess)
        assertEquals("red_bull", result.getOrThrow().first().teamId)
    }

    @Test
    fun `a 404 with a JSON detail body maps to Server with that detail`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"detail": "Season not found"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val result = repository.driverStandings(1900)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException.Server
        assertEquals(404, error.status)
        assertEquals("Season not found", error.detail)
        assertEquals("Season not found", repository.messageFor(error))
    }

    @Test
    fun `a 500 with no parseable body falls back to the generic server-error message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.driverStandings(2024)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException.Server
        assertEquals(500, error.status)
        assertEquals("Server error (500).", repository.messageFor(error))
    }

    @Test
    fun `a 401 always maps to the unauthorized message regardless of body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"detail": "invalid token"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val result = repository.driverStandings(2024)

        val error = result.exceptionOrNull() as ApiException.Server
        assertTrue(error.isUnauthorized)
        assertEquals(
            "The server rejected your API token. Check it in Settings.",
            repository.messageFor(error),
        )
    }

    @Test
    fun `malformed JSON maps to a Decoding failure with the decoding-error message`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"not": "a list"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val result = repository.driverStandings(2024)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException.Decoding)
        assertEquals("Couldn't read the data from the server.", repository.messageFor(result.exceptionOrNull()!!))
    }
}
