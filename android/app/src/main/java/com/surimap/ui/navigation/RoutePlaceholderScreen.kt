package com.surimap.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.surimap.ui.components.PoliAppBar
import com.surimap.ui.components.PoliBanner
import com.surimap.ui.components.PoliCard
import com.surimap.ui.theme.PoliDimens
import com.surimap.ui.theme.PoliFgMuted

@Composable
fun MarkerDetailRouteScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = "마커 상세",
        subtitle = "단서 마커 · 배수로 입구",
        onBack = onBack,
        modifier = modifier
    ) {
        PoliCard(strong = true) {
            Text(text = "마커 상세 route", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "권한 분기와 사진 첨부 UI는 AUI-T09에서 구현합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgMuted
            )
        }
    }
}

@Composable
fun BlockedOutboxRouteScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = "미전송 진단",
        subtitle = "처리 불가 항목만 표시",
        onBack = onBack,
        modifier = modifier
    ) {
        PoliBanner(text = "정상 오프라인 대기 큐는 이 화면에 직접 노출하지 않습니다.")
        PoliCard(strong = true) {
            Text(text = "처리 불가 2건", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "incident_closed 또는 police_phone_not_assigned처럼 자동 재전송으로 해결할 수 없는 항목만 진단합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoliFgMuted
            )
        }
    }
}

@Composable
private fun PlaceholderScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        PoliAppBar(title = title, subtitle = subtitle, showBack = true, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = PoliDimens.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(PoliDimens.Space4),
            content = content
        )
    }
}
