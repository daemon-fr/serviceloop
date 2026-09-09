package com.v16studio.serviceloop

import androidx.compose.ui.graphics.Color
import com.v16studio.serviceloop.ui.designsystem.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServiceLoopDesignSystemTest {
    @Test fun canonicalLightAndDarkRolesAreExact() {
        with(ServiceLoopUiTokens.LightColors) {
            assertEquals(Color(0xFFF4F7F7), canvas); assertEquals(Color(0xFFFFFFFF), surface)
            assertEquals(Color(0xFF182A30), textPrimary); assertEquals(Color(0xFF08666B), action)
            assertEquals(Color(0xFFE7F3F1), selection); assertEquals(Color(0xFF08747A), selectionOutline)
            assertEquals(Color(0xFF6A828C), outlineControl); assertEquals(Color(0xFFA32D35), errorInk)
        }
        with(ServiceLoopUiTokens.DarkColors) {
            assertEquals(Color(0xFF10191C), canvas); assertEquals(Color(0xFF19262B), surface)
            assertEquals(Color(0xFFEAF2F4), textPrimary); assertEquals(Color(0xFF79D4CE), action)
            assertEquals(Color(0xFF173C3D), selection); assertEquals(Color(0xFF79D4CE), selectionOutline)
            assertEquals(Color(0xFF809AA5), outlineControl); assertEquals(Color(0xFFFFC0C4), errorInk)
        }
    }

    @Test fun allThirtySevenCustomRolesExistInBothAppearances() {
        fun roles(c:ServiceLoopColorRoles)=listOf(c.canvas,c.surface,c.surfaceSubtle,c.surfaceRaised,c.textPrimary,c.textSecondary,c.textMuted,c.onAction,c.action,c.actionPressed,c.selection,c.onSelection,c.selectionOutline,c.outlineDecorative,c.outlineControl,c.icon,c.focus,c.disabledContainer,c.disabledText,c.neutralContainer,c.neutralInk,c.infoContainer,c.infoInk,c.warningContainer,c.warningInk,c.errorContainer,c.errorInk,c.destructive,c.onDestructive,c.successContainer,c.successInk,c.workingContainer,c.workingInk,c.historyContainer,c.historyInk,c.photoMat,c.scrimBase)
        val light=listOf(0xFFF4F7F7,0xFFFFFFFF,0xFFEDF2F3,0xFFFFFFFF,0xFF182A30,0xFF566870,0xFF586D75,0xFFFFFFFF,0xFF08666B,0xFF07565B,0xFFE7F3F1,0xFF075C62,0xFF08747A,0xFFD8E1E3,0xFF6A828C,0xFF566870,0xFF005A61,0xFFE6ECEE,0xFF75858B,0xFFEDF2F3,0xFF425D67,0xFFEAF0FA,0xFF315F96,0xFFFFF3DD,0xFF875100,0xFFFBE9E8,0xFFA32D35,0xFFA32D35,0xFFFFFFFF,0xFFE5F2EB,0xFF236347,0xFFE5F3F2,0xFF07666B,0xFFEEEAF4,0xFF66517F,0xFFE6ECEE,0xFF000000).map(::Color)
        val dark=listOf(0xFF10191C,0xFF19262B,0xFF223239,0xFF293C43,0xFFEAF2F4,0xFFB7C7CD,0xFF9AAFBA,0xFF062F32,0xFF79D4CE,0xFF69BDB8,0xFF173C3D,0xFFA4E6DF,0xFF79D4CE,0xFF344951,0xFF809AA5,0xFFB7C7CD,0xFFA4E6DF,0xFF2A383E,0xFF82969F,0xFF263A42,0xFFD1E0E5,0xFF20384F,0xFFB8D7FF,0xFF493519,0xFFFFDA97,0xFF49282D,0xFFFFC0C4,0xFFFFB3B9,0xFF3B0A12,0xFF173C30,0xFFA5E3BF,0xFF163B3E,0xFFA0E4DF,0xFF362E45,0xFFD9C9F1,0xFF0C1316,0xFF000000).map(::Color)
        assertEquals(light,roles(ServiceLoopUiTokens.LightColors))
        assertEquals(dark,roles(ServiceLoopUiTokens.DarkColors))
    }

    @Test fun materialMappingIsExplicitForContainerInverseErrorAndFixedRoles() {
        fun assertComplete(c:ServiceLoopColorRoles,dark:Boolean) {
            val scheme=if(dark)serviceLoopDarkColorScheme() else serviceLoopLightColorScheme()
            assertEquals(c.action,scheme.primary);assertEquals(c.onAction,scheme.onPrimary);assertEquals(c.selection,scheme.primaryContainer);assertEquals(c.onSelection,scheme.onPrimaryContainer)
            assertEquals(c.onSelection,scheme.secondary);assertEquals(if(dark)c.onAction else c.surface,scheme.onSecondary);assertEquals(c.selection,scheme.secondaryContainer);assertEquals(c.onSelection,scheme.onSecondaryContainer)
            assertEquals(c.historyInk,scheme.tertiary);assertEquals(if(dark)c.onAction else c.surface,scheme.onTertiary);assertEquals(c.historyContainer,scheme.tertiaryContainer);assertEquals(c.historyInk,scheme.onTertiaryContainer)
            assertEquals(c.canvas,scheme.background);assertEquals(c.textPrimary,scheme.onBackground);assertEquals(c.surface,scheme.surface);assertEquals(c.textPrimary,scheme.onSurface)
            assertEquals(c.surfaceSubtle,scheme.surfaceVariant);assertEquals(c.textSecondary,scheme.onSurfaceVariant);assertEquals(c.action,scheme.surfaceTint)
            assertEquals(c.canvas,scheme.surfaceDim);assertEquals(c.surfaceRaised,scheme.surfaceBright);assertEquals(c.canvas,scheme.surfaceContainerLowest);assertEquals(c.surface,scheme.surfaceContainerLow)
            assertEquals(c.surfaceSubtle,scheme.surfaceContainer);assertEquals(c.surfaceRaised,scheme.surfaceContainerHigh);assertEquals(c.surfaceRaised,scheme.surfaceContainerHighest)
            assertEquals(c.outlineControl,scheme.outline);assertEquals(c.outlineDecorative,scheme.outlineVariant)
            assertEquals(c.destructive,scheme.error);assertEquals(c.onDestructive,scheme.onError);assertEquals(c.errorContainer,scheme.errorContainer);assertEquals(c.errorInk,scheme.onErrorContainer);assertEquals(c.scrimBase,scheme.scrim)
            assertEquals(if(dark)Color(0xFFEDF2F3) else Color(0xFF223239),scheme.inverseSurface);assertEquals(if(dark)Color(0xFF182A30) else Color(0xFFEAF2F4),scheme.inverseOnSurface);assertEquals(if(dark)Color(0xFF08666B) else Color(0xFF79D4CE),scheme.inversePrimary)
            assertEquals(Color(0xFFE7F3F1),scheme.primaryFixed);assertEquals(Color(0xFFCCEBE5),scheme.primaryFixedDim);assertEquals(Color(0xFF075C62),scheme.onPrimaryFixed);assertEquals(c.textPrimary,scheme.onPrimaryFixedVariant)
            assertEquals(Color(0xFFE7F3F1),scheme.secondaryFixed);assertEquals(Color(0xFFCCEBE5),scheme.secondaryFixedDim);assertEquals(Color(0xFF075C62),scheme.onSecondaryFixed);assertEquals(Color(0xFF425D67),scheme.onSecondaryFixedVariant)
            assertEquals(Color(0xFFEEEAF4),scheme.tertiaryFixed);assertEquals(Color(0xFFDDD4EA),scheme.tertiaryFixedDim);assertEquals(Color(0xFF66517F),scheme.onTertiaryFixed);assertEquals(Color(0xFF44305D),scheme.onTertiaryFixedVariant)
        }
        assertComplete(ServiceLoopUiTokens.LightColors,false)
        assertComplete(ServiceLoopUiTokens.DarkColors,true)
    }

    @Test fun typographyAndGeometryMatchCanonicalTokens() {
        assertEquals(22f,ServiceLoopUiTokens.Typography.headlineSmall.fontSize.value,0f)
        assertEquals(28f,ServiceLoopUiTokens.Typography.headlineSmall.lineHeight.value,0f)
        assertEquals(16f,ServiceLoopUiTokens.Typography.bodyLarge.fontSize.value,0f)
        assertEquals(24f,ServiceLoopUiTokens.Typography.bodyLarge.lineHeight.value,0f)
        assertEquals(48f,ServiceLoopUiTokens.Size.touchMin.value,0f)
        assertEquals(16f,ServiceLoopUiTokens.Radius.card.value,0f)
        assertEquals(24f,ServiceLoopUiTokens.Layout.sectionGap.value,0f)
    }

    @Test fun selectionAndLifecycleStatusRemainIndependent() {
        val c=ServiceLoopUiTokens.LightColors
        val draft=serviceLoopStateStyle("DRAFT",c);val dispatched=serviceLoopStateStyle("DISPATCHED",c)
        assertEquals("Draft",draft.label);assertEquals(c.warningContainer,draft.container)
        assertEquals("Dispatched",dispatched.label);assertEquals(c.infoContainer,dispatched.container)
        assertNotEquals(c.selection,draft.container);assertNotEquals(c.selection,dispatched.container)
        assertEquals("Read-only",serviceLoopStateStyle("READ_ONLY",c).label)
        assertEquals("Unavailable",serviceLoopStateStyle("DISABLED",c).label)
    }
}
