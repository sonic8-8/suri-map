package com.surimap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.surimap.ui.components.PoliBannerVariant
import com.surimap.ui.components.PoliCard
import com.surimap.ui.components.PoliChip
import com.surimap.ui.components.PoliChipVariant
import com.surimap.ui.components.PoliDialog
import com.surimap.ui.components.PoliToast
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted

data class AppOverlayState(
    val incidentClosed: IncidentClosedOverlayState? = null,
    val blockedQueue: BlockedQueueToastState? = null,
    val handoverMemoSaved: HandoverMemoSavedToastState? = null,
    val searchPathEnded: SearchPathEndedToastState? = null
)

data class IncidentClosedOverlayState(
    val hasDraft: Boolean
)

data class BlockedQueueToastState(
    val blockedCount: Int
)

data class HandoverMemoSavedToastState(
    val pendingSync: Boolean
)

data class SearchPathEndedToastState(
    val pendingSync: Boolean
)

@Composable
fun AppOverlayHost(
    state: AppOverlayState,
    onDismissIncidentClosed: () -> Unit,
    onOpenBlockedQueue: () -> Unit,
    onDismissHandoverMemoSaved: () -> Unit,
    onDismissSearchPathEnded: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        state.blockedQueue?.let { toast ->
            PoliToast(
                text = "미전송 ${toast.blockedCount}건 처리 불가",
                actionText = "확인",
                onAction = onOpenBlockedQueue,
                modifier = Modifier.align(Alignment.TopCenter).padding(PoliDimens.SectionPadding),
                variant = PoliBannerVariant.Bad
            )
        }

        state.handoverMemoSaved?.let { toast ->
            PoliToast(
                text = if (toast.pendingSync) "인수인계 메모 저장됨 · 미전송" else "인수인계 메모 저장됨",
                actionText = "확인",
                onAction = onDismissHandoverMemoSaved,
                modifier = Modifier.align(Alignment.TopCenter).padding(PoliDimens.SectionPadding),
                variant = PoliBannerVariant.Info
            )
        }

        state.searchPathEnded?.let { toast ->
            PoliToast(
                text = if (toast.pendingSync) "수색 경로 종료 요청 저장됨" else "수색 경로가 종료되었습니다",
                actionText = "확인",
                onAction = onDismissSearchPathEnded,
                modifier = Modifier.align(Alignment.TopCenter).padding(PoliDimens.SectionPadding),
                variant = PoliBannerVariant.Info
            )
        }

        state.incidentClosed?.let { closed ->
            val draftText =
                if (closed.hasDraft) {
                    " 작성 중인 내용은 종료된 사건에 저장되지 않습니다."
                } else {
                    ""
                }
            PoliDialog(
                title = "사건이 종료되었습니다",
                body = "종료된 사건에는 더 이상 입력할 수 없습니다.$draftText",
                primaryText = "확인",
                onPrimary = onDismissIncidentClosed,
                modifier = Modifier.fillMaxSize(),
                danger = true
            )
        }
    }
}

@Composable
fun HandoverPromptBanner(onOpenHandover: () -> Unit, modifier: Modifier = Modifier) {
    PoliCard(modifier = modifier.fillMaxWidth().clickable(onClick = onOpenHandover), strong = true) {
        Column {
            Text(text = "이전 근무 기록 있음", style = MaterialTheme.typography.titleMedium)
            Text(text = "새 근무 시작 후 확인하지 않은 인수인계 기록이 있습니다.", style = MaterialTheme.typography.bodyMedium, color = PoliFgMuted)
        }
        Spacer(modifier = Modifier.padding(PoliDimens.Space1))
        PoliChip(text = "확인", variant = PoliChipVariant.Outbox, modifier = Modifier.align(Alignment.End))
    }
}
