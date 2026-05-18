package com.surimap.feature.bootstrap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliBrandMark
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
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
    ManagedConfigMissing,
    AuthenticationRequired
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
    val actionGuideText: String? = null,
    val retryEnabled: Boolean = false,
    val exitEnabled: Boolean = false,
    val shouldEnterIncidentList: Boolean = false,
    val requiresAuthentication: Boolean = false
) {
    fun visibleText(): List<String> =
        buildList {
            add(title)
            add(description)
            add(apiBaseUrl)
            failureMessage?.let(::add)
            add(primaryActionLabel)
            actionGuideText?.let(::add)
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
                    AuthBootstrapFailureReason.NotManagedPhone -> "관리 단말이 아닙니다.\nIT 부서로 문의 바랍니다."
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> "내부망 연결을 확인하세요."
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "해당 폴리폰으로 접속할 수 없습니다.\n단말 등록 또는 사건 배정 상태를 확인하세요.\n계속되면 IT 부서로 문의 바랍니다."
                    AuthBootstrapFailureReason.ManagedConfigMissing -> "관리 설정이 없습니다.\nIT 부서로 문의 바랍니다."
                    AuthBootstrapFailureReason.AuthenticationRequired -> "계정 인증이 필요합니다."
                }
            val retryable = reason == AuthBootstrapFailureReason.AuthenticationRequired
            return AuthBootstrapUiState(
                progress =
                when (reason) {
                    AuthBootstrapFailureReason.NotManagedPhone,
                    AuthBootstrapFailureReason.ManagedConfigMissing -> 0.2f
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> 0.55f
                    AuthBootstrapFailureReason.AuthenticationRequired -> 0.7f
                    AuthBootstrapFailureReason.ServerRejectedPhone -> 0.8f
                },
                title =
                when (reason) {
                    AuthBootstrapFailureReason.AuthenticationRequired -> "로그인 필요"
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "단말 확인 필요"
                    else -> "접속 확인 실패"
                },
                description =
                if (reason == AuthBootstrapFailureReason.AuthenticationRequired) {
                    "관리 단말과 내부망 확인이 완료되었습니다.\n계정 인증 후 사건 목록으로 이동합니다."
                } else {
                    "사건 정보는 접속 확인 후 표시됩니다."
                },
                steps = failureSteps(reason),
                failureMessage = message,
                apiBaseUrl = apiBaseUrl,
                primaryActionLabel =
                when (reason) {
                    AuthBootstrapFailureReason.AuthenticationRequired -> "로그인"
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "앱 종료"
                    else -> "확인 필요"
                },
                actionGuideText =
                when (reason) {
                    AuthBootstrapFailureReason.AuthenticationRequired -> null
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> "네트워크 상태를 확인해 주세요."
                    else -> null
                },
                retryEnabled = retryable,
                exitEnabled = reason == AuthBootstrapFailureReason.ServerRejectedPhone,
                requiresAuthentication = reason == AuthBootstrapFailureReason.AuthenticationRequired
            )
        }

        private fun failureSteps(reason: AuthBootstrapFailureReason): List<AuthCheckStep> =
            when (reason) {
                AuthBootstrapFailureReason.NotManagedPhone,
                AuthBootstrapFailureReason.ManagedConfigMissing ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "", AuthStepState.Failed),
                        AuthCheckStep("내부망 연결", "", AuthStepState.Checking),
                        AuthCheckStep("접속 권한 확인", "", AuthStepState.Checking)
                    )

                AuthBootstrapFailureReason.InternalNetworkUnavailable ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "", state = AuthStepState.Done),
                        AuthCheckStep("내부망 연결", "", AuthStepState.Failed),
                        AuthCheckStep("접속 권한 확인", "", AuthStepState.Checking)
                    )

                AuthBootstrapFailureReason.ServerRejectedPhone ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "", AuthStepState.Done),
                        AuthCheckStep("내부망 연결", "", AuthStepState.Done),
                        AuthCheckStep("접속 권한 확인", "", AuthStepState.Failed)
                    )

                AuthBootstrapFailureReason.AuthenticationRequired ->
                    listOf(
                        AuthCheckStep("관리 폴리폰 확인", "", AuthStepState.Done),
                        AuthCheckStep("내부망 연결", "", AuthStepState.Done),
                        AuthCheckStep("접속 권한 확인", "", AuthStepState.Checking)
                    )
            }
    }
}

@Composable
fun AuthBootstrapScreen(
    state: AuthBootstrapUiState,
    onRetry: () -> Unit = {},
    onExit: () -> Unit = {},
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
                    // Text(text = "현장 입력 앱", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
                }
            }

            PoliCard {
                Text(text = state.title, style = MaterialTheme.typography.titleMedium)
                Text(text = state.description, style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
                // Text(text = "내부망 API: ${state.apiBaseUrl}", style = MaterialTheme.typography.labelMedium, color = PoliFgMuted)
            }

            PoliCard {
                state.steps.forEach { step ->
                    PoliRow(title = step.title) {
                        PoliChip(text = step.state.label, variant = step.state.chipVariant)
                    }
                }
            }

            if (state.failureMessage != null) {
                PoliBanner(
                    text = state.failureMessage,
                    variant = PoliBannerVariant.Bad,
                    textAlign = TextAlign.Center
                )
            } else {
                PoliBanner(
                    text = "확인 완료 시 사건 선택 화면으로 자동 이동합니다.\n사건 상세는 접속 확인 전 표시하지 않습니다.",
                    variant = PoliBannerVariant.Warn
                )
            }
        }

        if (state.retryEnabled || state.requiresAuthentication) {
            PoliButton(
                text = state.primaryActionLabel,
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                variant = if (state.failureMessage == null) PoliButtonVariant.Secondary else PoliButtonVariant.Danger,
                enabled = state.retryEnabled
            )
        } else if (state.exitEnabled) {
            PoliButton(
                text = state.primaryActionLabel,
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                variant = PoliButtonVariant.Danger
            )
        } else if (state.failureMessage != null && state.actionGuideText != null) {
            PoliBanner(
                text = state.actionGuideText,
                variant = PoliBannerVariant.Bad,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val AuthStepState.label: String
    get() =
        when (this) {
            AuthStepState.Done -> "완료"
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
