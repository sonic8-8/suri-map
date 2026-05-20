package com.surimap.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun toastUsesDesignTokensForEachOperationalVariant() {
        val source = File("src/main/java/com/surimap/ui/components/PoliComponents.kt").readText()
        val toastIndex = source.indexOf("fun PoliToast(")

        assertTrue(toastIndex >= 0)
        assertTrue(source.indexOf("PoliBannerVariant.Info -> PoliPrimaryBorder", toastIndex) > toastIndex)
        assertTrue(source.indexOf("PoliBannerVariant.Warn -> PoliWarning", toastIndex) > toastIndex)
        assertTrue(source.indexOf("PoliBannerVariant.Bad -> PoliEmphasis", toastIndex) > toastIndex)
        assertTrue(source.indexOf("title: String? = null", toastIndex) > toastIndex)
        assertTrue(source.indexOf("actionText: String? = null", toastIndex) > toastIndex)
        assertTrue(source.indexOf("onAction: (() -> Unit)? = null", toastIndex) > toastIndex)
        assertTrue(source.indexOf("MaterialTheme.typography.labelLarge", toastIndex) > toastIndex)
        assertTrue(source.indexOf("PoliDimens.Space1", toastIndex) > toastIndex)
        assertTrue(source.indexOf("maxLines = if (title.isNullOrBlank()) 1 else 2", toastIndex) > toastIndex)
    }
}
