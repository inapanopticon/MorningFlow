package com.morningflow.app.feature.home

import androidx.annotation.DrawableRes
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

data class HomeModuleDisplayData(
    val title: String,
    val durationMinutes: Int,
)

data class HomeDisplayData(
    val date: LocalDate,
    val temperatureCelsius: Int,
    val location: String,
    val weatherCondition: String,
    val nextTodo: String,
    val remainingMinutes: Int,
    val readyBy: LocalTime,
    val earliestWakeUp: LocalTime = LocalTime.of(7, 0),
    val templateTitle: String,
    val modules: List<HomeModuleDisplayData>,
    @param:DrawableRes val illustration: Int?,
    val selectedNavigation: MorningFlowNavigationDestination,
    val selectedTemplateId: String? = null,
    val plannedDurationSeconds: Int? = null,
    val timeState: HomeTimeState = HomeTimeState.NORMAL,
) {
    val moduleCount: Int
        get() = modules.size

    val totalDurationMinutes: Int
        get() = plannedDurationSeconds?.let { (it / 60.0).roundToInt() }
            ?: modules.sumOf(HomeModuleDisplayData::durationMinutes)
}
