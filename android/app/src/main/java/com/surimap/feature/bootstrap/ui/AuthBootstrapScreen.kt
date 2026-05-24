package com.surimap.feature.bootstrap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliBrandMark
import com.surimap.ui.components.PoliButton
import com.surimap.ui.components.PoliButtonSize
import com.surimap.ui.components.PoliButtonVariant
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted
import com.surimap.ui.theme.PoliFgSecondary
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
            val authenticationRequired = reason == AuthBootstrapFailureReason.AuthenticationRequired
            val message =
                when (reason) {
                    AuthBootstrapFailureReason.NotManagedPhone -> "관리 단말이 아닙니다.\nIT 부서로 문의 바랍니다."
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> "내부망 연결을 확인하세요."
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "해당 폴리폰으로 접속할 수 없습니다.\n단말 등록 또는 사건 배정 상태를 확인하세요.\n계속되면 IT 부서로 문의 바랍니다."
                    AuthBootstrapFailureReason.ManagedConfigMissing -> "관리 설정이 없습니다.\nIT 부서로 문의 바랍니다."
                    AuthBootstrapFailureReason.AuthenticationRequired -> "보안 인증 후 배정 사건을 불러옵니다."
                }
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
                    AuthBootstrapFailureReason.AuthenticationRequired -> "폴리폰 인증"
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "단말 확인 필요"
                    else -> "접속 확인 실패"
                },
                description =
                if (authenticationRequired) {
                    "관리 단말과 내부망 확인이 완료되었습니다.\nSSO 인증을 완료하면 배정 사건을 불러옵니다."
                } else {
                    "사건 정보는 접속 확인 후 표시됩니다."
                },
                steps = failureSteps(reason),
                failureMessage = message.takeUnless { authenticationRequired },
                apiBaseUrl = apiBaseUrl,
                primaryActionLabel =
                when (reason) {
                    AuthBootstrapFailureReason.AuthenticationRequired -> "SSO 계정 인증"
                    AuthBootstrapFailureReason.ServerRejectedPhone -> "앱 종료"
                    else -> "확인 필요"
                },
                actionGuideText =
                when (reason) {
                    AuthBootstrapFailureReason.AuthenticationRequired ->
                        "보안 인증 화면에서 조직 계정을 확인합니다.\n인증이 끝나면 자동으로 앱으로 돌아옵니다."
                    AuthBootstrapFailureReason.InternalNetworkUnavailable -> "네트워크 상태를 확인해 주세요."
                    else -> null
                },
                retryEnabled = authenticationRequired,
                exitEnabled = reason == AuthBootstrapFailureReason.ServerRejectedPhone,
                requiresAuthentication = authenticationRequired
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
                        AuthCheckStep("계정 인증", "SSO 대기", AuthStepState.Checking)
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
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(PoliDimens.SectionPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
        ) {
            PoliBrandMark()
            Text(
                text = "Suri Map",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PoliFgMuted
            )
            Text(
                text = "수리맵",
                style = MaterialTheme.typography.displaySmall,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black
            )
            // Text(text = "현장 입력 앱", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(PoliDimens.Space5)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PoliDimens.Space2),
                verticalArrangement = Arrangement.spacedBy(PoliDimens.Space3)
            ) {
                state.steps.forEach { step ->
                    AuthStepRow(step = step)
                }
            }

            if (state.failureMessage != null) {
                PoliBanner(
                    text = state.failureMessage,
                    variant = PoliBannerVariant.Bad,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.retryEnabled || state.requiresAuthentication) {
                PoliButton(
                    text = state.primaryActionLabel,
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    variant = state.primaryActionButtonVariant,
                    size = PoliButtonSize.Large,
                    enabled = state.retryEnabled
                )
            } else if (state.exitEnabled) {
                PoliButton(
                    text = state.primaryActionLabel,
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    variant = PoliButtonVariant.Danger,
                    size = PoliButtonSize.Large
                )
            } else if (state.failureMessage != null && state.actionGuideText != null) {
                PoliBanner(
                    text = state.actionGuideText,
                    variant = PoliBannerVariant.Bad,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(PoliDimens.CtaHeightLarge))
            }
        }
    }
}

@Composable
private fun AuthStepRow(step: AuthCheckStep) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(PoliDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = step.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PoliFgSecondary
        )
        AuthStatusBadge(
            text = step.state.label,
            variant = step.state.chipVariant,
            showCheckingText = step.state == AuthStepState.Checking && step.title == "계정 인증"
        )
    }
}

@Composable
private fun AuthStatusBadge(text: String, variant: PoliChipVariant, showCheckingText: Boolean = false) {
    val (container, border, content) =
        when (variant) {
            PoliChipVariant.Good -> Triple(Color(0xFFE5F7EC), Color(0xFF8FD3AA), Color(0xFF1F6B45))
            PoliChipVariant.Warn -> Triple(Color(0xFFFFF0D7), Color(0xFFF0C47C), Color(0xFFA75D00))
            PoliChipVariant.Bad -> Triple(Color(0xFFFEE2E2), Color(0xFFE58A8A), Color(0xFFB91C1C))
            else -> Triple(Color(0xFFE8EEF4), Color(0xFFB6C4D2), Color(0xFF50657A))
        }

    if (variant == PoliChipVariant.Warn && !showCheckingText) {
        Box(
            modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        }
        return
    }

    Surface(
        modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val AuthBootstrapUiState.primaryActionButtonVariant: PoliButtonVariant
    get() =
        when {
            requiresAuthentication -> PoliButtonVariant.Primary
            failureMessage == null -> PoliButtonVariant.Secondary
            else -> PoliButtonVariant.Danger
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
