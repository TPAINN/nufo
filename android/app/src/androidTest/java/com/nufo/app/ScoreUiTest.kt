package com.nufo.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nufo.app.ui.screens.MethodologySheet
import com.nufo.app.ui.theme.NufoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI: the rule table the user sees matches the scoring code (docs/SCORING.md). */
@RunWith(AndroidJUnit4::class)
class ScoreUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun methodology_sheet_shows_the_current_rules() {
        // Arrange + Act
        rule.setContent { NufoTheme { MethodologySheet {} } }
        // Assert
        rule.onNodeWithText(rule.activity.getString(R.string.method_title)).assertExists()
        rule.onNodeWithText("−15 / −35 / −55 / −75").assertExists()
        rule.onNodeWithText("−5 / −10 / −15").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.method_rule_fat)).assertExists()
    }
}
