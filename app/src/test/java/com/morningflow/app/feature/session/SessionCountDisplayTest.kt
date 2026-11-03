package com.morningflow.app.feature.session

import com.morningflow.app.R
import com.morningflow.app.core.domain.runtime.SessionRuntimeState
import com.morningflow.app.core.domain.runtime.RuntimeStep
import com.morningflow.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SessionCountDisplayTest {
    private fun map(key: BehaviourKey, paused: Boolean = false): MorningSessionDisplayData {
        val module = RoutineModule(type = key.module, squatTarget = 8, stepTarget = 20,
            behaviours = listOf(Behaviour(key = key)))
        return requireNotNull(SessionDisplayMapper.map(SessionUiState(
            status = if (paused) SessionStatus.PAUSED else SessionStatus.RUNNING,
            modules = listOf(module), remainingSeconds = 42,
            sensor = SensorSnapshot(squatCount = 3, stepCount = 7)), false))
    }

    @Test fun squatsDisplayConfirmedCountInsteadOfRemainingTime() {
        val data = map(BehaviourKey.SQUATS)
        assertNull(data.timerText)
        assertEquals(R.string.session_count_format, data.secondaryDisplayText!!.resourceId)
        assertEquals(listOf(3, 8), data.secondaryDisplayText!!.formatArgs)
    }

    @Test fun walkingDisplaysConfirmedCountIncludingWhilePaused() {
        for (paused in listOf(false, true)) {
            val data = map(BehaviourKey.WALKING, paused)
            assertNull(data.timerText)
            assertEquals(listOf(7, 20), data.secondaryDisplayText!!.formatArgs)
        }
    }

    @Test fun timedBehavioursKeepCountdownEvenWithUnrelatedSensorCounts() {
        for (key in listOf(BehaviourKey.STRETCHING, BehaviourKey.LEAVE_BED)) {
            val data = map(key)
            assertNull(data.countText)
            assertEquals(R.string.session_time_format, data.secondaryDisplayText!!.resourceId)
            assertEquals(listOf(0, 42), data.secondaryDisplayText!!.formatArgs)
        }
    }
    @Test fun batchedSensorReadingsDoNotShowMoreThanTarget() {
        val module = RoutineModule(type = ModuleType.PHYSICAL_WAKE_UP, squatTarget = 10,
            behaviours = listOf(Behaviour(key = BehaviourKey.SQUATS)))
        val data = requireNotNull(SessionDisplayMapper.map(SessionUiState(status = SessionStatus.RUNNING,
            modules = listOf(module), sensor = SensorSnapshot(squatCount = 11)), false))
        assertEquals(listOf(10, 10), data.countText!!.formatArgs)
    }

    @Test fun unavailableSensorIsShownOnlyForTheCurrentCountingBehaviour() {
        for (key in listOf(BehaviourKey.WALKING, BehaviourKey.SQUATS, BehaviourKey.STRETCHING)) {
            val behaviour = Behaviour(key = key)
            val module = RoutineModule(type = key.module, behaviours = listOf(behaviour))
            val ui = SessionUiState(status = SessionStatus.RUNNING, modules = listOf(module))
            for (available in listOf(false, true)) {
                val runtime = SessionRuntimeState(status = SessionStatus.RUNNING,
                    steps = listOf(RuntimeStep(key.module, behaviour, 30, 10)),
                    sensorAvailable = available)
                val data = requireNotNull(SessionDisplayMapper.map(ui, false, runtime))
                val expected = !available && key != BehaviourKey.STRETCHING
                assertEquals(expected, data.availabilityMessages.isNotEmpty())
                if (expected) assertEquals(R.string.session_sensor_unavailable,
                    data.availabilityMessages.single().message.resourceId)
            }
        }
    }

}
