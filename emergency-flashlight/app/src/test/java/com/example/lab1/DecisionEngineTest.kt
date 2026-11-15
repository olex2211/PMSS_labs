package com.example.lab1

import com.example.lab1.decision.CloudCommandTracker
import com.example.lab1.decision.DecisionEngine
import com.example.lab1.decision.LightCommand
import com.example.lab1.decision.LightSource
import org.junit.Assert.*
import org.junit.Test

class DecisionEngineTest {
    private val engine = DecisionEngine()

    @Test fun shakeTurnsLightOnAndVibratesWhenAutomatic() {
        val decision = engine.onShake(automatic = true, currentlyOn = false)!!
        assertTrue(decision.enabled)
        assertTrue(decision.changed)
        assertTrue(decision.vibrate)
        assertEquals(LightSource.Shake, decision.source)
    }

    @Test fun shakeIsIgnoredWhenAutomaticIsOff() {
        assertNull(engine.onShake(automatic = false, currentlyOn = false))
    }

    @Test fun repeatedShakeWhileOnDoesNotToggleOff() {
        val decision = engine.onShake(automatic = true, currentlyOn = true)!!
        assertTrue(decision.enabled)
        assertFalse(decision.changed)
        assertTrue(decision.vibrate)
    }

    @Test fun manualOffTurnsLightOff() {
        val decision = engine.onCommand(LightCommand(enabled = false, source = LightSource.Manual), currentlyOn = true)
        assertFalse(decision.enabled)
        assertTrue(decision.changed)
        assertFalse(decision.vibrate)
    }

    @Test fun commandMatchingCurrentStateIsNotAChange() {
        val decision = engine.onCommand(LightCommand(enabled = true, source = LightSource.Cloud), currentlyOn = true)
        assertTrue(decision.enabled)
        assertFalse(decision.changed)
        assertEquals(LightSource.Cloud, decision.source)
    }

    @Test fun shakeAfterOffCommandTurnsLightOnAgain() {
        val off = engine.onCommand(LightCommand(enabled = false, source = LightSource.Ble), currentlyOn = true)
        assertFalse(off.enabled)
        val shake = engine.onShake(automatic = true, currentlyOn = off.enabled)!!
        assertTrue(shake.changed)
        assertTrue(shake.enabled)
    }

    @Test fun cloudTrackerUsesFirstReadAsBaseline() {
        val tracker = CloudCommandTracker()
        assertNull(tracker.onRead(null))
        assertNull(tracker.onRead(false))
    }

    @Test fun cloudTrackerReturnsOnlyChangedValues() {
        val tracker = CloudCommandTracker()
        tracker.onRead(false)
        assertEquals(true, tracker.onRead(true))
        assertNull(tracker.onRead(true))
        assertEquals(false, tracker.onRead(false))
        assertNull(tracker.onRead(false))
    }

    @Test fun cloudTrackerIgnoresMissingValueWithoutResettingBaseline() {
        val tracker = CloudCommandTracker()
        tracker.onRead(false)
        assertNull(tracker.onRead(null))
        assertEquals(true, tracker.onRead(true))
    }
}
