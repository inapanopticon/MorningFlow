package com.morningflow.app.feature.session.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.morningflow.app.core.designsystem.theme.MorningFlowDesignSystem
import com.morningflow.app.core.designsystem.theme.MorningFlowSpacing
import com.morningflow.app.core.designsystem.theme.illustrationFrame
import com.morningflow.app.feature.session.MorningSessionDisplayData
import com.morningflow.app.feature.session.SessionDisplayText

@Composable
internal fun SessionDisplayText.resolveText(): String =
    stringResource(resourceId, *formatArgs.toTypedArray())

@Composable
fun SessionGuidanceContent(
    data: MorningSessionDisplayData,
    modifier: Modifier = Modifier,
) {
    val colors = MorningFlowDesignSystem.colors
    val typography = MorningFlowDesignSystem.typography
    val heading = data.heading.resolveText()
    val secondary = data.secondaryDisplayText?.resolveText()

    Column(modifier = modifier.fillMaxWidth()) {
        // The illustration gives up height first, so short screens keep the text clear
        // of the control bar instead of pushing it underneath.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(data.illustration),
                contentDescription = null,
                modifier = Modifier
                    .widthIn(max = 390.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true).illustrationFrame(),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(Modifier.height(MorningFlowSpacing.Space32))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(heading)

                    if (secondary != null) {
                        append('\n')
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.Light),
                        ) {
                            append(secondary)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        if (data.displayLineCount >= 3) 157.dp else 111.dp,
                    ),
                color = colors.textPrimary,
                style = typography.pageTitle.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )

            data.guidanceBody?.let { guidance ->
                Text(
                    text = guidance.resolveText(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    color = colors.textSecondary,
                    style = typography.sessionGuidance,
                )
            }
        }
    }
}
