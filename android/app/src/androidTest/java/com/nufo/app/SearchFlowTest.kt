package com.nufo.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end: search answers from the bundled table at once, then opens a product with a score. */
@RunWith(AndroidJUnit4::class)
class SearchFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int) = rule.activity.getString(id)
    private fun seen(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun search_shows_local_matches_fast_and_opens_a_product() {
        // Arrange
        rule.waitUntil(10_000) { seen(s(R.string.welcome_start)) || seen(s(R.string.home_scan)) }
        if (seen(s(R.string.welcome_start))) rule.onNodeWithText(s(R.string.welcome_start)).performClick()
        rule.waitUntil(10_000) { seen(s(R.string.nav_search)) }
        rule.onAllNodesWithText(s(R.string.nav_search)).onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }

        // Act
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("yogurt")

        // Assert: the bundled USDA table answers within the debounce plus a few frames, whatever the network does.
        rule.waitUntil(3_000) { seen("kcal / 100 g") }
        rule.onAllNodesWithText("kcal / 100 g", substring = true).onFirst().performClick()
        rule.waitUntil(20_000) { seen(s(R.string.nufo_score)) }
    }
}
