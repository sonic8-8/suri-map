package com.surimap.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.surimap.ui.theme.PoliBgElevated
import com.surimap.ui.theme.PoliBgInput
import com.surimap.ui.theme.PoliBgSurface
import com.surimap.ui.theme.PoliBorder
import com.surimap.ui.theme.PoliBorderStrong
import com.surimap.ui.theme.PoliCurrent
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliEmphasis
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliOverlayDim
import com.surimap.ui.theme.PoliPrimary
import com.surimap.ui.theme.PoliPrimaryBorder
import com.surimap.ui.theme.PoliPrimaryFg
import com.surimap.ui.theme.PoliPrimaryFillSoft
import com.surimap.ui.theme.PoliPrimaryMid
import com.surimap.ui.theme.PoliSuccess
import com.surimap.ui.theme.PoliWarning

enum class PoliButtonVariant {
    Primary,
    Secondary,
    Danger
}

enum class PoliButtonSize {
    Small,
    Regular,
    Large
}

enum class PoliChipVariant {
    Neutral,
    Good,
    Warn,
    Bad,
    Outbox
}

enum class PoliBannerVariant {
    Info,
    Warn,
    Bad
}

@Composable
fun PoliButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PoliButtonVariant = PoliButtonVariant.Primary,
    size: PoliButtonSize = PoliButtonSize.Regular,
    enabled: Boolean = true
) {
    val height =
        when (size) {
            PoliButtonSize.Small -> PoliDimens.TouchGlove
            PoliButtonSize.Regular -> PoliDimens.CtaHeight
            PoliButtonSize.Large -> PoliDimens.CtaHeightLarge
        }

    val colors =
        when (variant) {
            PoliButtonVariant.Primary ->
                ButtonDefaults.buttonColors(
                    containerColor = PoliPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = PoliBgInput,
                    disabledContentColor = PoliFgMuted
                )
            PoliButtonVariant.Secondary ->
                ButtonDefaults.outlinedButtonColors(
                    contentColor = PoliPrimaryFg,
                    disabledContentColor = PoliFgMuted
                )
            PoliButtonVariant.Danger ->
                ButtonDefaults.buttonColors(
                    containerColor = PoliEmphasis,
                    contentColor = Color.White,
                    disabledContainerColor = PoliBgInput,
                    disabledContentColor = PoliFgMuted
                )
        }

    val shape = MaterialTheme.shapes.medium
    if (variant == PoliButtonVariant.Secondary) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(height),
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, if (enabled) PoliPrimaryBorder else PoliBorder),
            colors = colors
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(height),
            enabled = enabled,
            shape = shape,
            colors = colors
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PoliChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: PoliChipVariant = PoliChipVariant.Neutral
) {
    val (container, border, content) =
        when (variant) {
            PoliChipVariant.Neutral -> Triple(PoliBgInput, PoliBorder, PoliFgSecondary)
            PoliChipVariant.Good -> Triple(PoliBgInput, PoliSuccess, PoliSuccess)
            PoliChipVariant.Warn -> Triple(PoliBgInput, PoliWarning, PoliWarning)
            PoliChipVariant.Bad -> Triple(PoliBgInput, PoliEmphasis, PoliEmphasis)
            PoliChipVariant.Outbox -> Triple(PoliPrimaryFillSoft, PoliPrimaryBorder, PoliPrimaryFg)
        }

    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun PoliBanner(
    text: String,
    modifier: Modifier = Modifier,
    variant: PoliBannerVariant = PoliBannerVariant.Info,
    textAlign: TextAlign = TextAlign.Start
) {
    val border =
        when (variant) {
            PoliBannerVariant.Info -> PoliPrimaryBorder
            PoliBannerVariant.Warn -> PoliWarning
            PoliBannerVariant.Bad -> PoliEmphasis
        }
    val content =
        when (variant) {
            PoliBannerVariant.Info -> PoliFgSecondary
            PoliBannerVariant.Warn -> PoliWarning
            PoliBannerVariant.Bad -> PoliEmphasis
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgInput,
        contentColor = content,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(PoliDimens.Space4),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = textAlign
        )
    }
}

@Composable
fun PoliCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgSurface,
        contentColor = PoliFgPrimary,
        border = BorderStroke(1.dp, if (strong) PoliBorderStrong else PoliBorder)
    ) {
        Column(
            modifier = Modifier.padding(PoliDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            content = content
        )
    }
}

@Composable
fun PoliRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = PoliFgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliFgMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun PoliField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(PoliBgInput, MaterialTheme.shapes.medium)
            .padding(PoliDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = PoliFgPrimary)
    }
}

@Composable
fun PoliProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = PoliPrimaryFg,
        trackColor = PoliBgInput
    )
}

@Composable
fun PoliAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = PoliDimens.SectionPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Surface(
                modifier = Modifier.size(PoliDimens.TouchMin).clickable(onClick = onBack),
                shape = MaterialTheme.shapes.medium,
                color = PoliBgInput,
                border = BorderStroke(1.dp, PoliBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "<", style = MaterialTheme.typography.titleMedium, color = PoliFgPrimary)
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = PoliFgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoliFgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun PoliBrandMark(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = PoliPrimary,
        contentColor = PoliPrimaryFg,
        border = BorderStroke(1.dp, PoliPrimaryBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "수", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun PoliDialog(
    title: String,
    body: String,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    Box(modifier = modifier.background(PoliOverlayDim).padding(PoliDimens.SectionPadding)) {
        PoliCard(modifier = Modifier.align(Alignment.Center), strong = true) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) PoliEmphasis else PoliFgPrimary
            )
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PoliFgSecondary)
            PoliButton(
                text = primaryText,
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
                variant = if (danger) PoliButtonVariant.Danger else PoliButtonVariant.Primary
            )
        }
    }
}

@Composable
fun PoliBottomSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = PoliBgElevated,
        contentColor = PoliFgPrimary,
        border = BorderStroke(1.dp, PoliBorderStrong)
    ) {
        Column(
            modifier = Modifier.padding(PoliDimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
        ) {
            Box(
                modifier =
                Modifier
                    .width(PoliDimens.BottomSheetHandleWidth)
                    .height(PoliDimens.BottomSheetHandleHeight)
                    .background(PoliBorderStrong, MaterialTheme.shapes.small)
            )
            content()
        }
    }
}

@Composable
fun PoliToast(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PoliBannerVariant = PoliBannerVariant.Bad
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PoliBgElevated,
        contentColor = PoliFgPrimary,
        border = BorderStroke(1.dp, if (variant == PoliBannerVariant.Bad) PoliEmphasis else PoliPrimaryBorder)
    ) {
        Row(
            modifier = Modifier.padding(PoliDimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgSecondary
            )
            Text(
                text = actionText,
                modifier = Modifier.clickable(onClick = onAction),
                style = MaterialTheme.typography.labelLarge,
                color = if (variant == PoliBannerVariant.Bad) PoliEmphasis else PoliCurrent
            )
        }
    }
}
