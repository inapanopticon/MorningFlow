package com.morningflow.app.core.designsystem.illustration

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowShapes
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.MorningFlowTheme

private data class IllustrationPreviewItem(
    @param:DrawableRes val resourceId: Int,
    val resourceName: String,
)

private val sessionAndHomeIllustrations = listOf(
    IllustrationPreviewItem(MorningFlowIllustrations.LeaveBedStart, "ill_leave_bed_start"),
    IllustrationPreviewItem(MorningFlowIllustrations.LeaveBedActive, "ill_leave_bed_active"),
    IllustrationPreviewItem(MorningFlowIllustrations.LeaveBedLightReminder, "ill_leave_bed_light_reminder"),
    IllustrationPreviewItem(MorningFlowIllustrations.PhysicalWakeupStretching, "ill_physical_wakeup_stretching"),
    IllustrationPreviewItem(MorningFlowIllustrations.PhysicalWakeupWalking, "ill_physical_wakeup_walking"),
    IllustrationPreviewItem(MorningFlowIllustrations.PhysicalWakeupSquats, "ill_physical_wakeup_squats"),
    IllustrationPreviewItem(MorningFlowIllustrations.MorningReset, "ill_morning_reset"),
    IllustrationPreviewItem(MorningFlowIllustrations.MealPreparation, "ill_meal_preparation"),
    IllustrationPreviewItem(MorningFlowIllustrations.WashGroom, "ill_wash_groom"),
    IllustrationPreviewItem(MorningFlowIllustrations.Breakfast, "ill_breakfast"),
    IllustrationPreviewItem(MorningFlowIllustrations.GetReady, "ill_get_ready"),
    IllustrationPreviewItem(MorningFlowIllustrations.HomeOutsideWakeupWindow, "ill_home_outside_wakeup_window"),
    IllustrationPreviewItem(MorningFlowIllustrations.HomeInsufficientTime, "ill_home_insufficient_time"),
    IllustrationPreviewItem(MorningFlowIllustrations.ColdStart, "ill_cold_start"),
    IllustrationPreviewItem(MorningFlowIllustrations.MorningSessionComplete, "ill_morning_session_complete"),
    IllustrationPreviewItem(MorningFlowIllustrations.LeavingReminder, "ill_leaving_reminder"),
)

@Composable
private fun IllustrationCard(
    item: IllustrationPreviewItem,
    imageSize: Int,
    cardWidth: Int,
) {
    val colors = MorningFlowDesignSystem.colors

    Surface(
        modifier = Modifier.width(cardWidth.dp),
        color = colors.surface,
        shape = MorningFlowShapes.Radius14,
    ) {
        Column(
            modifier = Modifier.padding(MorningFlowSpacing.Space12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space8),
        ) {
            Box(
                modifier = Modifier
                    .size(imageSize.dp)
                    .border(1.dp, colors.borderSubtle, MorningFlowShapes.Radius8),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(item.resourceId),
                    contentDescription = item.resourceName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = item.resourceName,
                color = colors.textPrimary,
                style = MorningFlowDesignSystem.typography.body,
            )
        }
    }
}

@Composable
private fun IllustrationGrid(
    items: List<IllustrationPreviewItem>,
    columns: Int,
    imageSize: Int,
    cardWidth: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space16)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space16),
            ) {
                rowItems.forEach { item ->
                    IllustrationCard(item, imageSize, cardWidth)
                }
            }
        }
    }
}

@Preview(
    name = "MorningFlow Illustration Catalog",
    showBackground = true,
    backgroundColor = 0xFFFBF9F4,
    widthDp = 900,
    heightDp = 1260,
)
@Composable
private fun MorningFlowIllustrationCatalogPreview() {
    MorningFlowTheme {
        val colors = MorningFlowDesignSystem.colors
        val typography = MorningFlowDesignSystem.typography

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(MorningFlowSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(MorningFlowSpacing.Space24),
        ) {
            Text(
                text = "Session & Home Illustrations",
                color = colors.textPrimary,
                style = typography.sectionHeading,
            )
            IllustrationGrid(
                items = sessionAndHomeIllustrations,
                columns = 4,
                imageSize = 156,
                cardWidth = 196,
            )

        }
    }
}
