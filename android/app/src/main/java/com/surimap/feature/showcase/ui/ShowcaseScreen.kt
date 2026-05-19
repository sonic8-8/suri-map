package com.surimap.feature.showcase.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.surimap.feature.alert.ui.IncidentAlertBanner
import com.surimap.feature.alert.ui.IncidentAlertUiState
import com.surimap.feature.search.ui.SearchLayerKind
import com.surimap.feature.search.ui.SearchLifecycleStatus
import com.surimap.feature.search.ui.SearchMapLayerUiState
import com.surimap.feature.search.ui.SearchMapSyncStatus
import com.surimap.feature.search.ui.SearchMapUiState
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliBgBase
import com.surimap.ui.theme.PoliBgSurface
import com.surimap.ui.theme.PoliBorderStrong
import com.surimap.ui.theme.PoliCurrent
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliEmphasis
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgPrimary
import com.surimap.ui.theme.PoliFgSecondary
import com.surimap.ui.theme.PoliPrimaryBorder
import com.surimap.ui.theme.PoliPrimaryFillSoft
import com.surimap.ui.theme.PoliPrimaryFg
import com.surimap.ui.theme.PoliSuccess
import com.surimap.ui.theme.PoliWarning
import com.surimap.ui.theme.SuriMapTheme

@Composable
fun ShowcaseScreen(modifier: Modifier = Modifier) {
    val state = remember { showcaseState() }
    val transition = rememberInfiniteTransition(label = "showcase")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec =
        infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec =
        infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(showcaseBackdrop())
            .verticalScroll(rememberScrollState())
            .padding(bottom = PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4)
    ) {
        PoliAppBar(
            title = state.missingPersonSummary,
            subtitle = "${state.incidentTitle} · ${state.opLabel}",
            trailing = {
                PoliChip(text = "미리보기", variant = PoliChipVariant.Good)
            }
        )

        PoliBanner(
            text = "정적 쇼케이스 화면입니다. 로그인, 심박, 미전송, 실시간 타일은 모두 건너뜁니다.",
            variant = PoliBannerVariant.Info,
            modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
        )

        ShowcaseHeroPanel(
            state = state,
            pulse = pulse,
            glow = glow,
            modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
        )

        ShowcaseMetrics(
            state = state,
            modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
        )

        IncidentAlertBanner(
            state = state.incidentAlert!!,
            onConfirm = {},
            onOpenMap = {},
            modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
        )

        PoliCard(
            modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding),
            strong = true
        ) {
            Text(
                text = "미리보기 안내",
                style = MaterialTheme.typography.titleMedium,
                color = PoliFgPrimary
            )
            PoliRow(
                title = "목적",
                subtitle = "실행 의존성 없이 화면 톤만 확인합니다."
            )
            PoliRow(
                title = "데이터",
                subtitle = "고정 샘플 데이터만 사용합니다."
            )
            PoliRow(
                title = "동작",
                subtitle = "이 화면은 의도적으로 더 이상 진행되지 않습니다."
            )
        }

        PoliBanner(
            text = "`suriMapDebugShowcase` 플래그를 끄면 일반 디버그 흐름으로 돌아갑니다.",
            variant = PoliBannerVariant.Warn,
            modifier = Modifier.padding(horizontal = PoliDimens.SectionPadding)
        )
    }
}

@Composable
private fun ShowcaseHeroPanel(
    state: SearchMapUiState,
    pulse: Float,
    glow: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 520.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = PoliBgSurface,
        contentColor = PoliFgPrimary,
        border = BorderStroke(1.dp, PoliBorderStrong)
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0A1320),
                            Color(0xFF10243D),
                            Color(0xFF0B1728)
                        )
                    )
                )
        ) {
            ShowcaseSceneCanvas(
                pulse = pulse,
                glow = glow,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(PoliDimens.Space4),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
            ) {
                PoliChip(text = "정적 화면", variant = PoliChipVariant.Good)
                PoliChip(text = "백엔드 없음", variant = PoliChipVariant.Warn)
                state.layers.take(3).forEach { layer ->
                    PoliChip(
                        text = layer.label,
                        variant = if (layer.highlighted) PoliChipVariant.Outbox else PoliChipVariant.Neutral
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(PoliDimens.Space4),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
            ) {
                PoliChip(text = state.syncStatus.showcaseLabel(), variant = state.syncStatus.toChipVariant())
                PoliChip(text = state.lifecycleStatus.showcaseLabel(), variant = PoliChipVariant.Outbox)
                PoliChip(text = "미리보기", variant = PoliChipVariant.Warn)
            }

            Surface(
                modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(PoliDimens.Space4)
                    .fillMaxWidth(0.72f),
                shape = MaterialTheme.shapes.large,
                color = Color(0xCC08111F),
                contentColor = PoliFgPrimary,
                border = BorderStroke(1.dp, PoliBorderStrong)
            ) {
                Column(
                    modifier = Modifier.padding(PoliDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)
                ) {
                    Text(
                        text = state.missingPersonSummary,
                        style = MaterialTheme.typography.titleLarge,
                        color = PoliFgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.incidentTitle} · ${state.opLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PoliFgSecondary
                    )
                    Text(
                        text = state.movementSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PoliFgMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                        PoliChip(text = state.dutyShiftLabel, variant = PoliChipVariant.Good)
                        PoliChip(text = state.assignmentLabel, variant = PoliChipVariant.Outbox)
                    }
                }
            }

            Surface(
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(PoliDimens.Space4)
                    .widthIn(max = 180.dp),
                shape = MaterialTheme.shapes.large,
                color = PoliPrimaryFillSoft,
                contentColor = PoliPrimaryFg,
                border = BorderStroke(1.dp, PoliPrimaryBorder)
            ) {
                Column(
                    modifier = Modifier.padding(PoliDimens.Space4),
                    verticalArrangement = Arrangement.spacedBy(PoliDimens.Space1),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "미리보기",
                        style = MaterialTheme.typography.labelMedium,
                        color = PoliPrimaryFg
                    )
                    Text(
                        text = "레이어 ${state.layers.size}개 · ${state.elapsedLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PoliFgPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowcaseSceneCanvas(
    pulse: Float,
    glow: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val minDimension = size.minDimension

        drawRect(
            brush =
            Brush.linearGradient(
                colors =
                listOf(
                    Color(0xFF0D1827),
                    Color(0xFF12253C),
                    Color(0xFF0A1320)
                )
            )
        )

        drawCircle(
            color = PoliCurrent.copy(alpha = glow * 0.18f),
            radius = minDimension * 0.34f,
            center = Offset(width * 0.83f, height * 0.16f)
        )
        drawCircle(
            color = PoliWarning.copy(alpha = glow * 0.12f),
            radius = minDimension * 0.28f,
            center = Offset(width * 0.18f, height * 0.78f)
        )
        drawCircle(
            color = PoliSuccess.copy(alpha = glow * 0.08f),
            radius = minDimension * 0.22f,
            center = Offset(width * 0.72f, height * 0.70f)
        )

        val gridColor = Color.White.copy(alpha = 0.05f)
        val verticalStep = (width / 8f).coerceAtLeast(72f)
        var x = 0f
        while (x <= width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += verticalStep
        }
        val horizontalStep = (height / 7f).coerceAtLeast(72f)
        var y = 0f
        while (y <= height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += horizontalStep
        }

        repeat(4) { band ->
            drawPath(
                path = contourPath(width, height, band),
                color = Color.White.copy(alpha = 0.045f + band * 0.008f),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawRoundRect(
            color = Color(0x1122D3EE),
            topLeft = Offset(width * 0.08f, height * 0.20f),
            size = Size(width * 0.36f, height * 0.18f),
            cornerRadius = CornerRadius(36f, 36f)
        )
        drawRoundRect(
            color = Color(0x0D22C55E),
            topLeft = Offset(width * 0.52f, height * 0.30f),
            size = Size(width * 0.30f, height * 0.22f),
            cornerRadius = CornerRadius(32f, 32f)
        )

        val routePoints =
            listOf(
                Offset(width * 0.15f, height * 0.74f),
                Offset(width * 0.28f, height * 0.60f),
                Offset(width * 0.45f, height * 0.66f),
                Offset(width * 0.63f, height * 0.44f),
                Offset(width * 0.82f, height * 0.37f)
            )
        val routePath = Path().apply {
            moveTo(routePoints.first().x, routePoints.first().y)
            cubicTo(
                routePoints[0].x + width * 0.04f,
                routePoints[0].y - height * 0.10f,
                routePoints[1].x - width * 0.03f,
                routePoints[1].y + height * 0.02f,
                routePoints[2].x,
                routePoints[2].y
            )
            cubicTo(
                routePoints[2].x + width * 0.05f,
                routePoints[2].y - height * 0.12f,
                routePoints[3].x - width * 0.04f,
                routePoints[3].y + height * 0.04f,
                routePoints[4].x,
                routePoints[4].y
            )
        }
        drawPath(
            path = routePath,
            color = PoliCurrent.copy(alpha = 0.22f),
            style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = routePath,
            color = PoliCurrent.copy(alpha = 0.88f),
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        routePoints.forEachIndexed { index, point ->
            val radius = if (index == 2) 10f * pulse else 6.5f
            val accent =
                when (index) {
                    0 -> PoliPrimaryFg
                    1 -> PoliSuccess
                    2 -> PoliCurrent
                    3 -> PoliWarning
                    else -> PoliEmphasis
                }
            drawCircle(color = Color.Black.copy(alpha = 0.28f), radius = radius + 5f, center = point)
            drawCircle(color = accent.copy(alpha = 0.95f), radius = radius, center = point)
            drawCircle(color = Color.White.copy(alpha = 0.75f), radius = 2.2f, center = point)
        }

        val pulseCenter = Offset(width * 0.63f, height * 0.44f)
        drawCircle(
            color = PoliCurrent.copy(alpha = 0.24f),
            radius = 22f * pulse,
            center = pulseCenter,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = PoliCurrent.copy(alpha = 0.10f),
            radius = 46f * pulse,
            center = pulseCenter,
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun ShowcaseMetrics(
    state: SearchMapUiState,
    modifier: Modifier = Modifier
) {
    val metrics =
        listOf(
            ShowcaseMetricCardModel("동기화", state.syncStatus.showcaseLabel(), "실시간 데이터는 사용하지 않습니다."),
            ShowcaseMetricCardModel("상태", state.lifecycleStatus.showcaseLabel(), "고정 쇼케이스 카드입니다."),
            ShowcaseMetricCardModel("레이어", "${state.layers.size}", "전체 / 부대 / 팀 / 경로 / 마커"),
            ShowcaseMetricCardModel("알림", if (state.incidentAlert != null) "1" else "0", "알림 배너 미리보기를 사용합니다.")
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            ShowcaseMetricCard(metrics[0], modifier = Modifier.weight(1f))
            ShowcaseMetricCard(metrics[1], modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3)) {
            ShowcaseMetricCard(metrics[2], modifier = Modifier.weight(1f))
            ShowcaseMetricCard(metrics[3], modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShowcaseMetricCard(
    model: ShowcaseMetricCardModel,
    modifier: Modifier = Modifier
) {
    PoliCard(modifier = modifier, strong = true) {
        Text(
            text = model.label,
            style = MaterialTheme.typography.labelMedium,
            color = PoliFgMuted
        )
        Text(
            text = model.value,
            style = MaterialTheme.typography.titleLarge,
            color = PoliFgPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = model.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = PoliFgSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class ShowcaseMetricCardModel(
    val label: String,
    val value: String,
    val detail: String
)

private fun showcaseState(): SearchMapUiState =
    SearchMapUiState.active(
        syncStatus = SearchMapSyncStatus.Offline,
        unsentCount = 1,
        oldestPendingMinutes = 2,
        hasUnreadHandover = true,
        incidentAlert = IncidentAlertUiState.personFound(markerId = "mk-showcase-person-found-001")
    ).copy(
        incidentTitle = "종로구 묘동",
        missingPersonSummary = "실종자 정보 확인 중",
        opLabel = "OP 03 · 재수색",
        dutyShiftLabel = "근무조 · 14:00-18:00",
        assignmentLabel = "담당: 기동대 1부대 A팀",
        elapsedLabel = "04:21",
        movementSummary = "도보 1.2 km",
        layers =
        listOf(
            SearchMapLayerUiState("전체 수색 구역", SearchLayerKind.Overall, overlayId = "overall"),
            SearchMapLayerUiState("부대 수색 구역", SearchLayerKind.Unit, overlayId = "unit"),
            SearchMapLayerUiState("A팀 수색 경로", SearchLayerKind.Team, highlighted = true, overlayId = "team"),
            SearchMapLayerUiState("현재 수색 경로", SearchLayerKind.Path, highlighted = true, overlayId = "path"),
            SearchMapLayerUiState(
                "실종자 마커",
                SearchLayerKind.Marker,
                highlighted = true,
                overlayId = "mk-showcase-person-found-001",
                geoJson = """{"type":"Point","coordinates":[126.9164,35.1631]}"""
            )
        ),
        focusedMarkerId = "mk-showcase-person-found-001",
        blockedOutboxCount = 0,
        bottomPanelExpanded = true,
        mapOverlaysVisible = true
    )

private fun SearchMapSyncStatus.showcaseLabel(): String =
    when (this) {
        SearchMapSyncStatus.Idle -> "대기"
        SearchMapSyncStatus.Offline -> "미전송 ${1}건 · ${2}분"
        SearchMapSyncStatus.Sending -> "전송 중"
        SearchMapSyncStatus.Synced -> "동기화"
    }

private fun SearchMapSyncStatus.toChipVariant(): PoliChipVariant =
    when (this) {
        SearchMapSyncStatus.Idle -> PoliChipVariant.Neutral
        SearchMapSyncStatus.Offline -> PoliChipVariant.Warn
        SearchMapSyncStatus.Sending -> PoliChipVariant.Outbox
        SearchMapSyncStatus.Synced -> PoliChipVariant.Good
    }

private fun SearchLifecycleStatus.showcaseLabel(): String =
    when (this) {
        SearchLifecycleStatus.Active -> "현장 기록"
        SearchLifecycleStatus.Paused -> "수색 일시정지"
        SearchLifecycleStatus.Stopped -> "수색 종료"
        SearchLifecycleStatus.OpRequired -> "OP 확인 필요"
        SearchLifecycleStatus.OpTransition -> "OP 전환 확인 필요"
    }

private fun showcaseBackdrop(): Brush =
    Brush.verticalGradient(
        colors =
        listOf(
            PoliBgBase,
            Color(0xFF081524),
            Color(0xFF07101D)
        )
    )

private fun contourPath(width: Float, height: Float, band: Int): Path {
    val path = Path()
    val baseY = height * (0.16f + band * 0.17f)
    path.moveTo(width * -0.06f, baseY)
    path.cubicTo(
        width * 0.12f,
        baseY - height * 0.04f,
        width * 0.27f,
        baseY + height * 0.05f,
        width * 0.44f,
        baseY - height * 0.03f
    )
    path.cubicTo(
        width * 0.60f,
        baseY - height * 0.09f,
        width * 0.78f,
        baseY + height * 0.04f,
        width * 1.08f,
        baseY - height * 0.02f
    )
    return path
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ShowcaseScreenPreview() {
    SuriMapTheme {
        ShowcaseScreen()
    }
}
