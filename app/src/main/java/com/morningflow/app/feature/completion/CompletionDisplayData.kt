package com.morningflow.app.feature.completion

import androidx.annotation.DrawableRes
import java.time.LocalTime

data class CompletionDisplayData(
    @param:DrawableRes val illustration: Int,
    val actualDurationMinutes: Int,
    val finishedAt: LocalTime,
)
