package com.owlmedia.racecontrol

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.owlmedia.racecontrol.core.design.RaceControlTheme
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.feature.standings.StandingRow
import org.junit.Rule
import org.junit.Test

/**
 * Vertical smoke test for the Compose counterpart of iOS `LoadableView`:
 * [LoadableContent] is the one primitive every screen in the app renders its
 * loading / error / loaded states through (see its doc comment in
 * `core/ui/StateViews.kt`), so exercising it here -- rather than one specific
 * screen's Hilt-wired ViewModel -- covers the same loading -> content -> error
 * vertical slice for every feature at once, with no DI/network wiring needed.
 *
 * [StandingRow] (from the Standings feature) stands in for "real content" in
 * the loaded case, doubling as a check that its accessibility
 * `contentDescription` (added alongside the iOS chart-accessibility work) is
 * actually exposed to the accessibility tree / test framework.
 */
class LoadableContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test
    fun loadingStateShowsTheLoadingIndicatorAndLabel() {
        composeRule.setContent {
            RaceControlTheme {
                LoadableContent<Unit>(
                    state = UiState.Loading,
                    onRetry = {},
                ) { Text("content should not render while loading") }
            }
        }

        composeRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
        composeRule.onAllNodesWithText("content should not render while loading").assertCountEquals(0)
    }

    @Test
    fun failedStateShowsTheErrorMessageAndRetryInvokesTheCallback() {
        var retried = false

        composeRule.setContent {
            RaceControlTheme {
                LoadableContent<Unit>(
                    state = UiState.Failed("Can't reach the server"),
                    onRetry = { retried = true },
                ) { Text("content should not render while failed") }
            }
        }

        composeRule.onNodeWithText("Can't reach the server").assertIsDisplayed()
        composeRule.onAllNodesWithText("content should not render while failed").assertCountEquals(0)

        composeRule.onNodeWithText(string(R.string.try_again)).performClick()
        assert(retried) { "Expected tapping the retry button to invoke onRetry" }
    }

    @Test
    fun loadedStateRendersContentWithAnAccessibleDescription() {
        composeRule.setContent {
            RaceControlTheme {
                LoadableContent(
                    state = UiState.Loaded(Unit),
                    onRetry = {},
                ) {
                    StandingRow(
                        rank = 1,
                        title = "Max Verstappen",
                        subtitle = "Red Bull Racing",
                        points = "227",
                        wins = 7,
                        fraction = 1f,
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription(
                "Position 1, Max Verstappen, Red Bull Racing, 227 points, 7 wins",
            )
            .assertIsDisplayed()
    }
}
