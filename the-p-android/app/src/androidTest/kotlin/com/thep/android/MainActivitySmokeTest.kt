package com.thep.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun actualApplicationStartsAndNavigatesWithoutPermissions() {
        compose.waitUntil(120000) {
            compose
                .onAllNodesWithText("编辑课表")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        compose.onNodeWithText("The P").assertIsDisplayed()
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("设置与数据").assertIsDisplayed()
        compose.onNodeWithText("进度", useUnmergedTree = true).performClick()
        compose.onNodeWithText("训练进度").assertIsDisplayed()
    }
}
