package com.surimap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.surimap.feature.outbox.ui.BlockedOutboxScreen
import com.surimap.feature.outbox.ui.sampleBlockedOutboxUiState

@Composable
fun BlockedOutboxRouteScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BlockedOutboxScreen(
        state = sampleBlockedOutboxUiState(),
        onBack = onBack,
        onOpenSupportGuide = {},
        modifier = modifier
    )
}
