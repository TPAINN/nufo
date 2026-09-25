package com.nufo.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end over the real Open Food Facts API (needs network): manual barcode entry, the result
 * screen with real nutrition, then the offline history. Camera frames can't be faked here, so the
 * scanner's s(R.string.scan_type) path stands in for a live scan.
 */
@RunWith(AndroidJUnit4::class)
class ScanFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** Labels come from the app's resources so the test passes in Greek and English. */
    private fun s(id: Int) = rule.activity.getString(id)

    private fun waitFor(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun passWelcomeIfShown() {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(s(R.string.welcome_start)).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText(s(R.string.home_scan)).fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText(s(R.string.welcome_start)).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText(s(R.string.welcome_start)).performClick()
        }
        waitFor(s(R.string.home_scan))
    }

    @Test fun scanByCode_showsRealNutrition_andSavesToHistory() {
        passWelcomeIfShown()
        rule.onNodeWithText(s(R.string.home_scan)).performClick()
        waitFor(s(R.string.scan_type))
        rule.onNodeWithText(s(R.string.scan_type)).performClick()
        waitFor(s(R.string.scan_enter))
        rule.onNode(hasSetTextAction()).performTextInput("3017620422003")
        rule.onNodeWithText(s(R.string.scan_lookup)).performClick()

        // Result screen: name, official grades and the source footer come from Open Food Facts.
        waitFor(s(R.string.why_score))
        rule.onNodeWithContentDescription("Nutella", substring = true).assertExists() // Calligraph title exposes its text as a description
        // Scale descriptions read "Nutri-Score E. <explanation>"; a missing Eco-Score is announced by title only.
        rule.onNodeWithContentDescription("Nutri-Score E", substring = true).assertExists()
        rule.onNodeWithContentDescription("NOVA 4", substring = true).assertExists()

        rule.onNodeWithContentDescription(s(R.string.back)).performClick()
        waitFor(s(R.string.nav_history))
        rule.onAllNodesWithText(s(R.string.nav_history)).onFirst().performClick()
        waitFor(s(R.string.history_swipe))
        rule.onAllNodesWithText("Nutella").onFirst().assertExists()
    }

    @Test fun manualEntry_rejectsInvalidBarcode() {
        passWelcomeIfShown()
        rule.onNodeWithText(s(R.string.home_scan)).performClick()
        waitFor(s(R.string.scan_type))
        rule.onNodeWithText(s(R.string.scan_type)).performClick()
        waitFor(s(R.string.scan_enter))
        rule.onNode(hasSetTextAction()).performTextInput("12ab")
        rule.onNodeWithText(s(R.string.scan_lookup)).assertIsNotEnabled()
    }
}
