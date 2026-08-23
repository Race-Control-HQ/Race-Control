package com.owlmedia.racecontrol

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.RaceControlApi
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import com.owlmedia.racecontrol.feature.standings.StandingsMode
import com.owlmedia.racecontrol.feature.standings.StandingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * Vertical smoke test for [StandingsViewModel]'s driver-standings slice:
 * Idle -> Loading -> Loaded, and Idle -> Loading -> Failed, driven through a
 * real [RaceControlRepository] backed by [MockWebServer] (same approach as
 * `RaceControlRepositoryTest`) rather than a hand-mocked repository, so the
 * whole vertical slice -- Retrofit decoding, repository error-mapping,
 * ViewModel state transitions -- is exercised together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13's newest supported SDK; project targetSdk is 35.
class StandingsViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var viewModel: StandingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

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

        val repository = RaceControlRepository(
            api = api,
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = testDispatcher,
        )
        viewModel = StandingsViewModel(repository)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun driverStandingsJson() = """
        [
          {
            "position": 1, "points": 227, "wins": 7,
            "driverId": "max_verstappen", "driverCode": "VER",
            "givenName": "Max", "familyName": "Verstappen",
            "teamName": "Red Bull Racing", "teamId": "red_bull"
          }
        ]
    """.trimIndent()

    @Test
    fun `loading drivers goes Idle then Loading then Loaded`() = runTest {
        server.enqueue(MockResponse().setBody(driverStandingsJson()).setHeader("Content-Type", "application/json"))

        viewModel.drivers.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.load(2024, StandingsMode.DRIVERS)
            assertEquals(UiState.Loading, awaitItem())

            val loaded = awaitItem()
            assertTrue(loaded is UiState.Loaded)
            val standings = (loaded as UiState.Loaded).value
            assertEquals(1, standings.size)
            assertEquals("max_verstappen", standings.first().driverId)
        }
    }

    @Test
    fun `loading drivers goes Idle then Loading then Failed on a server error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("""{"detail": "boom"}""")
                .setHeader("Content-Type", "application/json"),
        )

        viewModel.drivers.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.load(2024, StandingsMode.DRIVERS)
            assertEquals(UiState.Loading, awaitItem())

            val failed = awaitItem()
            assertTrue(failed is UiState.Failed)
            assertEquals("boom", (failed as UiState.Failed).message)
        }
    }

    @Test
    fun `a second load for an already-loaded year does not re-hit the transport`() = runTest {
        server.enqueue(MockResponse().setBody(driverStandingsJson()).setHeader("Content-Type", "application/json"))

        viewModel.drivers.test {
            awaitItem() // Idle
            viewModel.load(2024, StandingsMode.DRIVERS)
            awaitItem() // Loading
            awaitItem() // Loaded

            viewModel.load(2024, StandingsMode.DRIVERS)
            expectNoEvents()
        }
        assertEquals(1, server.requestCount)
    }
}
