package com.resdownload.android.core.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ScalePredictiveBackLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedBackRevealsBackground() {
        val fixture = setScaleBackContent()

        composeRule.runOnIdle { fixture.dispatcher.onBackPressed() }

        composeRule.waitUntil(timeoutMillis = 3_000) { !fixture.foregroundVisible }
        composeRule.onNodeWithTag("scaleBackBackground").assertIsDisplayed()
        composeRule.onNodeWithTag("scaleBackForeground").assertDoesNotExist()
    }

    @Test
    fun cancelledGestureKeepsForeground() {
        val fixture = setScaleBackContent()

        composeRule.runOnIdle {
            fixture.dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_RIGHT),
            )
        }
        composeRule.runOnIdle {
            fixture.dispatcher.dispatchOnBackProgressed(
                BackEventCompat(300f, 500f, 0.65f, BackEventCompat.EDGE_RIGHT),
            )
        }
        composeRule.runOnIdle { fixture.dispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()

        assertFalse(fixture.backCommitted)
        composeRule.onNodeWithTag("scaleBackForeground").assertIsDisplayed()
    }

    @Test
    fun rapidSecondBackReachesBackgroundHandler() {
        val fixture = setScaleBackContent()

        composeRule.runOnIdle {
            fixture.dispatcher.onBackPressed()
            fixture.dispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 3_000) {
            fixture.backgroundBackCount == 1
        }
        composeRule.runOnIdle { assertFalse(fixture.foregroundVisible) }
    }

    private fun setScaleBackContent(): ScaleBackFixture {
        val fixture = ScaleBackFixture()
        composeRule.setContent {
            val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { fixture.dispatcher = owner.onBackPressedDispatcher }
            MaterialTheme {
                BackHandler(enabled = !fixture.foregroundVisible) {
                    fixture.backgroundBackCount++
                }
                ScalePredictiveBackLayout(
                    enabled = fixture.foregroundVisible,
                    onBack = {
                        fixture.backCommitted = true
                        fixture.foregroundVisible = false
                    },
                    contentKey = fixture.foregroundVisible,
                    keepBackgroundComposed = true,
                    background = { modifier ->
                        Box(modifier.testTag("scaleBackBackground"))
                    },
                ) { modifier ->
                    if (fixture.foregroundVisible) {
                        Box(modifier.testTag("scaleBackForeground"))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return fixture
    }
}

private class ScaleBackFixture {
    lateinit var dispatcher: OnBackPressedDispatcher
    var foregroundVisible by mutableStateOf(true)
    var backCommitted by mutableStateOf(false)
    var backgroundBackCount by mutableStateOf(0)
}
