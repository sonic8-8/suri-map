package com.surimap.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PoliComponentVariantContractTest {
    @Test
    fun commonComponentVariantsCoverOperationalStates() {
        assertEquals(
            listOf(PoliButtonVariant.Primary, PoliButtonVariant.Secondary, PoliButtonVariant.Danger),
            PoliButtonVariant.entries.toList()
        )
        assertEquals(
            listOf(
                PoliChipVariant.Neutral,
                PoliChipVariant.Good,
                PoliChipVariant.Warn,
                PoliChipVariant.Bad,
                PoliChipVariant.Outbox
            ),
            PoliChipVariant.entries.toList()
        )
        assertEquals(
            listOf(PoliBannerVariant.Info, PoliBannerVariant.Warn, PoliBannerVariant.Bad),
            PoliBannerVariant.entries.toList()
        )
    }
}
