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

enum class AuthBootstrapFailureReason {
    NotManagedPhone,
    InternalNetworkUnavailable,
    ServerRejectedPhone,
    ManagedConfigMissing
}

sealed interface AuthBootstrapOutcome {
    data class Ready(
        val policePhoneId: String,
        val accessToken: String? = null
    ) : AuthBootstrapOutcome
    data class Blocked(val reason: AuthBootstrapFailureReason) : AuthBootstrapOutcome
}

data class AuthBootstrapUiState(
    val progress: Float,
    val title: String = "접속 확인 중",
    val description: String = "관리 폴리폰 상태와 내부망 연결을 확인하고 있습니다.",
    val steps: List<AuthCheckStep>,
    val failureMessage: String? = null,
    val apiBaseUrl: String,
    val primaryActionLabel: String = "확인 중",
    val retryEnabled: Boolean = false,
    val shouldEnterIncidentList: Boolean = false
) {
    fun visibleText(): List<String> =
        buildList {
            add(title)
            add(description)
            add(apiBaseUrl)
            failureMessage?.let(::add)
            add(primaryActionLabel)
            steps.forEach { step ->
                add(step.title)
                add(step.subtitle)
                add(step.state.label)
            }
        }

    companion object {
        fun checking(apiBaseUrl: String): AuthBootstrapUiState =
            AuthBootstrapUiState(
                progress = 0.5f,
                steps =
                listOf(
                    AuthCheckStep("관리 폴리폰 확인", "관리 설정 확인", AuthStepState.Checking),
                    AuthCheckStep("내부망 연결", "API 도달 확인", AuthStepState.Checking),
                    AuthCheckStep("접속 권한 확인", "폴리폰 상태 확인", AuthStepState.Checking)
                ),
                apiBaseUrl = apiBaseUrl
            )

        fun fromOutcome(outcome: AuthBootstrapOutcome, apiBaseUrl: String): AuthBootstrapUiState =
            when (outcome) {
                is AuthBootstrapOutcome.Ready ->
                    AuthBootstrapUiState(
                        progress = 1f,
                        title = "접속 확인 완료",
                        description = "사건 선택 화면으로 이동합니다.",
                        steps =
                        listOf(
                            AuthCheckStep("관리 폴리폰 확인", "관리 설정 확인", AuthStepState.Done),
                            AuthCheckStep("내부망 연결", "API 도달 확인", AuthStepState.Done),
                            AuthCheckStep("접속 권한 확인", "폴리폰 상태 확인", AuthStepState.Done)
                        ),
                        apiBaseUrl = apiBaseUrl,
                        primaryActionLabel = "이동 중",
                        shouldEnterIncidentList = true
                    )

                is AuthBootstrapOutcome.Blocked ->
                    failureState(reason = outcome.reason, apiBaseUrl = apiBaseUrl)
            }

        private fun failureState(
            reason: AuthBootstrapFailureReason,
            apiBaseUrl: String
        ): AuthBootstrapUiState {
            val message =
                when (reason) {
                    AuthBootstrapFailureReason.NotManagedPhone -> "관리 단말이 아닙니다. IT 부서 문의"
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> "내부망 연결을 확인하세요"
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "이 폴리폰으로 접속할 수 없습니다. IT 부서 문의"
                    AuthBootstrapFailureReason.ManagedConfigMissing -> "관리 설정이 없습니다. IT 부서 문의"
                }
            val retryable = reason == AuthBootstrapFailureReason.InternalNetworkUnavailable
            return AuthBootstrapUiState(
                progress =
                when (reason) {
                    AuthBootstrapFailureReason.NotManagedPhone,
                    AuthBootstrapFailureReason.ManagedConfigMissing -> 0.2f
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> 0.55f
                    AuthBootstrapFailureReason.ServerRejectedPhone -> 0.8f
                },
                title = "접속 확인 실패",
                description = "사건 정보는 접속 확인 후 표시됩니다.",
                steps = failureSteps(reason),
                failureMessage = message,
                apiBaseUrl = apiBaseUrl,
                primaryActionLabel = if (retryable) "네트워크 다시 확인" else "IT 부서 문의",
                retryEnabled = retryable
            )
        }

        private fun failureSteps(reason: AuthBootstrapFailureReason): List<AuthCheckStep> =
            when (reason) {
                AuthBootstrapFailureReason.NotManagedPhone,
                AuthBootstrapFailureReason.ManagedConfigMissing ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "관리 설정 확인", AuthStepState.Failed),
                        AuthCheckStep("내부망 연결", "API 도달 확인", AuthStepState.Checking),
                        AuthCheckStep("접속 권한 확인", "폴리폰 상태 확인", AuthStepState.Checking)
                    )

                AuthBootstrapFailureReason.InternalNetworkUnavailable ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "관리 설정 확인", AuthStepState.Done),
                        AuthCheckStep("내부망 연결", "API 도달 확인", AuthStepState.Failed),
                        AuthCheckStep("접속 권한 확인", "폴리폰 상태 확인", AuthStepState.Checking)
                    )

                AuthBootstrapFailureReason.ServerRejectedPhone ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "관리 설정 확인", AuthStepState.Done),
                        AuthCheckStep("내부망 연결", "API 도달 확인", AuthStepState.Done),
                        AuthCheckStep("접속 권한 확인", "폴리폰 상태 확인", AuthStepState.Failed)
                    )
            }
    }
}

@Composable
fun AuthBootstrapScreen(
    state: AuthBootstrapUiState,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                Text(text = "내부망 API: ${state.apiBaseUrl}", style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
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
                    text = "확인 완료 시 사건 선택 화면으로 자동 이동합니다. 사건 상세는 접속 확인 전 표시하지 않습니다.",
                    variant = PoliBannerVariant.Warn
                )
            }
        }

        PoliButton(
            text = state.primaryActionLabel,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            variant = if (state.failureMessage == null) PoliButtonVariant.Secondary else PoliButtonVariant.Danger,
            enabled = state.retryEnabled
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
    AuthBootstrapUiState.checking(apiBaseUrl = "https://suri-map.internal")

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun AuthBootstrapPreview() {
    SuriMapTheme {
        AuthBootstrapScreen(state = sampleAuthBootstrapState())
    }
}
