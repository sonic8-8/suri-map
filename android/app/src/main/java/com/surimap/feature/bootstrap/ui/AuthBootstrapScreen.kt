package com.surimap.feature.bootstrap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliBrandMark
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliProgress
import com.surimap.ui.components.PoliRow
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.SuriMapTheme

data class AuthCheckStep(
    val title: String,
    val subtitle: String,
    val state: AuthStepState
)

enum class AuthStepState {
    Done,
    Checking,
    Failed
}

data class AuthBootstrapUiState(
    val progress: Float,
    val title: String = "접속 확인 중",
    val description: String = "관리 폴리폰 상태와 내부망 연결을 확인하고 있습니다.",
    val steps: List<AuthCheckStep>,
    val failureMessage: String? = null
)

@Composable
fun AuthBootstrapScreen(state: AuthBootstrapUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space4)) {
                PoliBrandMark()
                Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space2)) {
                    Text(text = "수리맵", style = MaterialTheme.typography.displaySmall)
                    Text(text = "현장 입력 앱", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
                }
            }

            PoliCard {
                Text(text = state.title, style = MaterialTheme.typography.titleMedium)
                Text(text = state.description, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
            }

            PoliCard {
                PoliProgress(progress = state.progress)
                Spacer(modifier = Modifier.height(PoliDimens.Space2))
                state.steps.forEach { step ->
                    PoliRow(title = step.title, subtitle = step.subtitle) {
                        PoliChip(text = step.state.label, variant = step.state.chipVariant)
                    }
                }
            }

            if (state.failureMessage != null) {
                PoliBanner(text = state.failureMessage, variant = PoliBannerVariant.Bad)
            } else {
                PoliBanner(
                    text = "확인 완료 시 사건 선택 화면으로 자동 이동합니다. 사건명과 OP는 접속 확인 전 표시하지 않습니다.",
                    variant = PoliBannerVariant.Warn
                )
            }
        }

        PoliButton(
            text = if (state.failureMessage == null) "확인 중" else "IT 부서 문의",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            variant = if (state.failureMessage == null) PoliButtonVariant.Secondary else PoliButtonVariant.Danger,
            enabled = false
        )
    }
}

private val AuthStepState.label: String
    get() =
        when (this) {
            AuthStepState.Done -> "확인"
            AuthStepState.Checking -> "확인 중"
            AuthStepState.Failed -> "실패"
        }

private val AuthStepState.chipVariant: PoliChipVariant
    get() =
        when (this) {
            AuthStepState.Done -> PoliChipVariant.Good
            AuthStepState.Checking -> PoliChipVariant.Warn
            AuthStepState.Failed -> PoliChipVariant.Bad
        }

fun sampleAuthBootstrapState() =
    AuthBootstrapUiState(
        progress = 0.8f,
        steps =
        listOf(
            AuthCheckStep("관리 폴리폰 확인", "단말 등록 상태 확인", AuthStepState.Done),
            AuthCheckStep("내부망 연결", "경찰 내부망 도달 확인", AuthStepState.Checking)
        )
    )

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun AuthBootstrapPreview() {
    SuriMapTheme {
        AuthBootstrapScreen(state = sampleAuthBootstrapState())
    }
}
