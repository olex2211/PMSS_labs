package com.example.lab1

import com.example.lab1.flashlight.FlashlightViewModel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FlashlightScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun simulatedLampSwitchesOffWhenActivityPauses() {
        lateinit var model: FlashlightViewModel
        rule.runOnIdle { model = ViewModelProvider(rule.activity)[FlashlightViewModel::class.java] }
        assumeTrue("This scenario exercises a device without a hardware flash", model.state.value.torch.simulated)
        rule.onNodeWithText("Light is off").assertIsDisplayed()
        rule.onNodeWithText("Turn on flashlight").performClick()
        rule.onNodeWithText("Light is on").assertIsDisplayed()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(model.state.value.sensorActive)
            assertFalse(model.state.value.torch.enabled)
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText("Light is off").assertIsDisplayed()
        rule.runOnIdle { if (model.state.value.sensorAvailable) assertTrue(model.state.value.sensorActive) }
    }
}
