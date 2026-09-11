package com.v16studio.serviceloop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import com.v16studio.serviceloop.ui.designsystem.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class ServiceLoopDesignSystemTest {
    @Test fun brandStripWordmarkColorsHaveReadableContrastInBothThemes() {
        fun contrast(foreground:Color,background:Color):Float {
            val high=maxOf(foreground.luminance(),background.luminance())
            val low=minOf(foreground.luminance(),background.luminance())
            return (high+.05f)/(low+.05f)
        }
        assertEquals(true,contrast(ServiceLoopUiTokens.LightColors.textPrimary,ServiceLoopUiTokens.LightColors.brandBand)>=4.5f)
        assertEquals(true,contrast(ServiceLoopUiTokens.LightColors.action,ServiceLoopUiTokens.LightColors.brandBand)>=4.5f)
        assertEquals(true,contrast(ServiceLoopUiTokens.DarkColors.textPrimary,ServiceLoopUiTokens.DarkColors.brandBand)>=4.5f)
        assertEquals(true,contrast(ServiceLoopUiTokens.DarkColors.action,ServiceLoopUiTokens.DarkColors.brandBand)>=4.5f)
    }
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

    @Test fun allFortyOneCustomRolesExistInBothAppearances() {
        fun roles(c:ServiceLoopColorRoles)=listOf(c.canvas,c.surface,c.surfaceSubtle,c.surfaceRaised,c.textPrimary,c.textSecondary,c.textMuted,c.onAction,c.action,c.actionPressed,c.selection,c.onSelection,c.selectionOutline,c.outlineDecorative,c.outlineControl,c.icon,c.focus,c.disabledContainer,c.disabledText,c.neutralContainer,c.neutralInk,c.infoContainer,c.infoInk,c.warningContainer,c.warningInk,c.errorContainer,c.errorInk,c.destructive,c.onDestructive,c.successContainer,c.successInk,c.workingContainer,c.workingInk,c.historyContainer,c.historyInk,c.photoMat,c.scrimBase,c.brandBand,c.recordBorder,c.tonalCommandContainer,c.tonalCommandInk)
        val light=listOf(0xFFF4F7F7,0xFFFFFFFF,0xFFEDF2F3,0xFFFFFFFF,0xFF182A30,0xFF566870,0xFF586D75,0xFFFFFFFF,0xFF08666B,0xFF07565B,0xFFE7F3F1,0xFF075C62,0xFF08747A,0xFFD8E1E3,0xFF6A828C,0xFF566870,0xFF005A61,0xFFE6ECEE,0xFF75858B,0xFFEDF2F3,0xFF425D67,0xFFEAF0FA,0xFF315F96,0xFFFFF3DD,0xFF875100,0xFFFBE9E8,0xFFA32D35,0xFFA32D35,0xFFFFFFFF,0xFFE5F2EB,0xFF236347,0xFFE5F3F2,0xFF07666B,0xFFEEEAF4,0xFF66517F,0xFFE6ECEE,0xFF000000,0xFFF0F8F7,0xFF67B9B8,0xFFD7EFED,0xFF075C62).map(::Color)
        val dark=listOf(0xFF10191C,0xFF19262B,0xFF223239,0xFF293C43,0xFFEAF2F4,0xFFB7C7CD,0xFF9AAFBA,0xFF062F32,0xFF79D4CE,0xFF69BDB8,0xFF173C3D,0xFFA4E6DF,0xFF79D4CE,0xFF344951,0xFF809AA5,0xFFB7C7CD,0xFFA4E6DF,0xFF2A383E,0xFF82969F,0xFF263A42,0xFFD1E0E5,0xFF20384F,0xFFB8D7FF,0xFF493519,0xFFFFDA97,0xFF49282D,0xFFFFC0C4,0xFFFFB3B9,0xFF3B0A12,0xFF173C30,0xFFA5E3BF,0xFF163B3E,0xFFA0E4DF,0xFF362E45,0xFFD9C9F1,0xFF0C1316,0xFF000000,0xFF152629,0xFF4D8D8A,0xFF234B4D,0xFFA4E6DF).map(::Color)
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
        fun dp(expected:Float,actual:androidx.compose.ui.unit.Dp)=assertEquals(expected,actual.value,0f)
        listOf(0f,2f,4f,8f,12f,16f,20f,24f,32f,40f,48f).zip(listOf(ServiceLoopUiTokens.Space.none,ServiceLoopUiTokens.Space.hair,ServiceLoopUiTokens.Space.xs,ServiceLoopUiTokens.Space.sm,ServiceLoopUiTokens.Space.md,ServiceLoopUiTokens.Space.lg,ServiceLoopUiTokens.Space.xl,ServiceLoopUiTokens.Space.section,ServiceLoopUiTokens.Space.major,ServiceLoopUiTokens.Space.hero,ServiceLoopUiTokens.Space.large)).forEach{dp(it.first,it.second)}
        listOf(8f,12f,16f,24f,999f).zip(listOf(ServiceLoopUiTokens.Radius.badge,ServiceLoopUiTokens.Radius.field,ServiceLoopUiTokens.Radius.card,ServiceLoopUiTokens.Radius.dialog,ServiceLoopUiTokens.Radius.pill)).forEach{dp(it.first,it.second)}
        listOf(1f,1f,1.5f,2f,2f).zip(listOf(ServiceLoopUiTokens.Stroke.divider,ServiceLoopUiTokens.Stroke.outline,ServiceLoopUiTokens.Stroke.selected,ServiceLoopUiTokens.Stroke.focus,ServiceLoopUiTokens.Stroke.record)).forEach{dp(it.first,it.second)}
        val sizes=listOf(48f,20f,24f,32f,24f,48f,52f,64f,64f,64f,36f,80f,64f,64f,88f,56f,560f,560f,196f,320f,640f,840f,360f,600f,840f,480f,148f)
        val actualSizes=listOf(ServiceLoopUiTokens.Size.touchMin,ServiceLoopUiTokens.Size.iconSmall,ServiceLoopUiTokens.Size.icon,ServiceLoopUiTokens.Size.iconLarge,ServiceLoopUiTokens.Size.checkboxGlyph,ServiceLoopUiTokens.Size.buttonMin,ServiceLoopUiTokens.Size.buttonPrimaryMin,ServiceLoopUiTokens.Size.fieldMin,ServiceLoopUiTokens.Size.pickerMin,ServiceLoopUiTokens.Size.topBarMin,ServiceLoopUiTokens.Size.brandMin,ServiceLoopUiTokens.Size.bottomNavMin,ServiceLoopUiTokens.Size.listRowMin,ServiceLoopUiTokens.Size.photoThumb,ServiceLoopUiTokens.Size.photoGridMin,ServiceLoopUiTokens.Size.editorActionReserve,ServiceLoopUiTokens.Size.sheetMaxWidth,ServiceLoopUiTokens.Size.dialogMaxWidth,ServiceLoopUiTokens.Size.menuMinWidth,ServiceLoopUiTokens.Size.menuMaxWidth,ServiceLoopUiTokens.Size.formMaxWidth,ServiceLoopUiTokens.Size.contentMaxWidth,ServiceLoopUiTokens.Size.narrowThreshold,ServiceLoopUiTokens.Size.mediumThreshold,ServiceLoopUiTokens.Size.expandedThreshold,ServiceLoopUiTokens.Size.compactHeightThreshold,ServiceLoopUiTokens.Size.pairMinCellWidth)
        sizes.zip(actualSizes).forEach{dp(it.first,it.second)}
        val layouts=listOf(16f,24f,32f,12f,24f,8f,16f,16f,8f,160f)
        val actualLayouts=listOf(ServiceLoopUiTokens.Layout.pageInsetCompact,ServiceLoopUiTokens.Layout.pageInsetMedium,ServiceLoopUiTokens.Layout.pageInsetExpanded,ServiceLoopUiTokens.Layout.bodyGap,ServiceLoopUiTokens.Layout.sectionGap,ServiceLoopUiTokens.Layout.buttonGap,ServiceLoopUiTokens.Layout.cardPadding,ServiceLoopUiTokens.Layout.barPadding,ServiceLoopUiTokens.Layout.pairGap,ServiceLoopUiTokens.Layout.bodyMinimumVisibleHeight)
        layouts.zip(actualLayouts).forEach{dp(it.first,it.second)}
        assertEquals(1.3f,ServiceLoopUiTokens.Layout.fontScaleStackThreshold,0f);assertEquals(1.5f,ServiceLoopUiTokens.Layout.badgeFontScaleStackThreshold,0f)
        assertEquals(listOf(80,120,180,180,0,0),listOf(ServiceLoopUiTokens.Motion.pressMs,ServiceLoopUiTokens.Motion.smallMs,ServiceLoopUiTokens.Motion.containerMs,ServiceLoopUiTokens.Motion.dialogMs,ServiceLoopUiTokens.Motion.routeMs,ServiceLoopUiTokens.Motion.reducedMs))
        assertEquals(androidx.compose.animation.core.CubicBezierEasing(.2f,0f,0f,1f).transform(.5f),ServiceLoopUiTokens.Motion.easing.transform(.5f),.0001f)
        listOf(0f,3f,6f,0f).zip(listOf(ServiceLoopUiTokens.Elevation.rest,ServiceLoopUiTokens.Elevation.menu,ServiceLoopUiTokens.Elevation.dialog,ServiceLoopUiTokens.Elevation.bottomBar)).forEach{dp(it.first,it.second)}
        assertEquals(listOf(.08f,.04f,.4f,.64f),listOf(ServiceLoopUiTokens.Alpha.pressed,ServiceLoopUiTokens.Alpha.hover,ServiceLoopUiTokens.Alpha.scrimLight,ServiceLoopUiTokens.Alpha.scrimDark))
        fun type(style:androidx.compose.ui.text.TextStyle,size:Float,line:Float,weight:androidx.compose.ui.text.font.FontWeight,spacing:Float=0f){assertEquals(size,style.fontSize.value,0f);assertEquals(line,style.lineHeight.value,0f);assertEquals(weight,style.fontWeight);assertEquals(spacing,style.letterSpacing.value,0f)}
        type(ServiceLoopUiTokens.Type.screenTitle,22f,28f,androidx.compose.ui.text.font.FontWeight.Bold);type(ServiceLoopUiTokens.Type.heroCount,32f,40f,androidx.compose.ui.text.font.FontWeight.Bold);type(ServiceLoopUiTokens.Type.sectionTitle,18f,24f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(ServiceLoopUiTokens.Type.itemTitle,16f,22f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(ServiceLoopUiTokens.Type.body,16f,24f,androidx.compose.ui.text.font.FontWeight.Normal);type(ServiceLoopUiTokens.Type.supporting,14f,20f,androidx.compose.ui.text.font.FontWeight.Normal);type(ServiceLoopUiTokens.Type.label,14f,20f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(ServiceLoopUiTokens.Type.meta,12f,18f,androidx.compose.ui.text.font.FontWeight.Normal);type(ServiceLoopUiTokens.Type.dayHeading,12f,18f,androidx.compose.ui.text.font.FontWeight.Bold,.6f);type(ServiceLoopUiTokens.Type.button,16f,22f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(ServiceLoopUiTokens.Type.identifier,14f,20f,androidx.compose.ui.text.font.FontWeight.Medium);type(ServiceLoopUiTokens.Type.badge,12f,16f,androidx.compose.ui.text.font.FontWeight.SemiBold)
    }

    @Test fun canonicalButtonContractUsesExactStateFamilies() {
        val c=ServiceLoopUiTokens.LightColors
        assertEquals(12f,ServiceLoopButtonContract.radius.value,0f);assertEquals(20f,ServiceLoopButtonContract.horizontalPadding.value,0f);assertEquals(12f,ServiceLoopButtonContract.verticalPadding.value,0f)
        assertEquals(52f,ServiceLoopButtonContract.primaryMinHeight.value,0f);assertEquals(48f,ServiceLoopButtonContract.secondaryMinHeight.value,0f);assertEquals(2f,ServiceLoopButtonContract.focusWidth.value,0f);assertEquals(2f,ServiceLoopButtonContract.focusGap.value,0f)
        assertEquals(c.action,ServiceLoopButtonContract.primaryContainer(c,true,false));assertEquals(c.actionPressed,ServiceLoopButtonContract.primaryContainer(c,true,true));assertEquals(c.disabledContainer,ServiceLoopButtonContract.primaryContainer(c,false,false));assertEquals(c.onAction,ServiceLoopButtonContract.primaryInk(c,true));assertEquals(c.disabledText,ServiceLoopButtonContract.primaryInk(c,false))
        assertEquals(c.tonalCommandContainer,ServiceLoopButtonContract.secondaryContainer(c,true));assertEquals(c.tonalCommandInk,ServiceLoopButtonContract.secondaryInk(c,true));assertEquals(c.disabledContainer,ServiceLoopButtonContract.secondaryContainer(c,false));assertEquals(c.disabledText,ServiceLoopButtonContract.secondaryInk(c,false));assertEquals(ServiceLoopUiTokens.Type.button,ServiceLoopButtonContract.textStyle)
    }

    @Test fun richButtonAdaptersDelegateToTheCanonicalContentPrimitive() {
        val source=File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopCompatibility.kt").readText()
        assertEquals(2,Regex("= ServiceLoopButtonContent\\(").findAll(source).count())
        assertEquals(false,source.contains("= androidx.compose.material3.Button("))
        assertEquals(false,source.contains("= androidx.compose.material3.OutlinedButton("))
        assertEquals(ServiceLoopUiTokens.Type.button,ServiceLoopButtonContract.textStyle)
        assertEquals(52f,ServiceLoopButtonContract.primaryMinHeight.value,0f)
        assertEquals(48f,ServiceLoopButtonContract.secondaryMinHeight.value,0f)
    }

    @Test fun ownerR1SharedVisualContractsStayStructural() {
        val components=File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopComponents.kt").readText()
        val app=File("src/main/java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt").readText()
        val daily=File("src/main/java/com/v16studio/serviceloop/ui/DailyOperationsUi.kt").readText()
        assertEquals(true,components.contains("maxIntrinsicWidth"))
        assertEquals(false,components.contains("label.length"))
        assertEquals(true,components.contains("PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))"))
        assertEquals(true,components.contains("Saved on this device · \$time"))
        assertEquals(true,components.contains("softWrap = false"))
        assertEquals(false,app.contains("title.contains("))
        assertEquals(true,app.contains("DetailScaffold(\"Service\", nav)"))
        assertEquals(true,components.contains("maxOf(leftWidthPx, rightWidthPx)"))
        assertEquals(true,app.contains("ServiceLoopBrandStrip()"))
        assertEquals(true,app.contains("ServiceLoopContentTabs"))
        assertEquals(true,daily.contains("ServiceLoopEntityRecord"))
        assertEquals(true,daily.contains("ServiceLoopSectionDivider"))
    }

    @Test fun c07AdapterContractsAreCanonical() {
        assertEquals(14f,ServiceLoopUiTokens.Type.label.fontSize.value,0f)
        assertEquals(20f,ServiceLoopUiTokens.Type.label.lineHeight.value,0f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold,ServiceLoopUiTokens.Type.label.fontWeight)
        assertEquals(24f,ServiceLoopUiTokens.Size.icon.value,0f)
        assertEquals(48f,ServiceLoopUiTokens.Size.touchMin.value,0f)
    }

    @Test fun responsivePageInsetsUseCanonicalThresholds() {
        assertEquals(16f, serviceLoopPageInset(320.dp).value, 0f)
        assertEquals(16f, serviceLoopPageInset(599.dp).value, 0f)
        assertEquals(24f, serviceLoopPageInset(600.dp).value, 0f)
        assertEquals(24f, serviceLoopPageInset(839.dp).value, 0f)
        assertEquals(32f, serviceLoopPageInset(840.dp).value, 0f)
    }

    @Test fun scaffoldPaddingAddsOnlyTheAdaptiveInsetDelta() {
        val base = PaddingValues(top = 64.dp, bottom = 80.dp)
        val medium = serviceLoopAdaptiveScaffoldPadding(base, 700.dp, LayoutDirection.Ltr)
        val expanded = serviceLoopAdaptiveScaffoldPadding(base, 900.dp, LayoutDirection.Ltr)
        assertEquals(8f, medium.calculateLeftPadding(LayoutDirection.Ltr).value, 0f)
        assertEquals(16f, expanded.calculateLeftPadding(LayoutDirection.Ltr).value, 0f)
        assertEquals(64f, expanded.calculateTopPadding().value, 0f)
        assertEquals(80f, expanded.calculateBottomPadding().value, 0f)
    }

    @Test fun unknownStateIsUnavailableAndUsesErrorFamily() {
        val c=ServiceLoopUiTokens.LightColors;val state=serviceLoopStateStyle("SOMETHING_NEW",c)
        assertEquals("State unavailable",state.label);assertEquals(ServiceLoopVisualState.Error,state.state);assertEquals(c.errorContainer,state.container);assertEquals(c.errorInk,state.ink)
    }

    @Test fun selectionAndLifecycleStatusRemainIndependent() {
        val c=ServiceLoopUiTokens.LightColors
        val draft=serviceLoopStateStyle("DRAFT",c);val dispatched=serviceLoopStateStyle("DISPATCHED",c)
        assertEquals("Draft",draft.label);assertEquals(c.warningContainer,draft.container)
        assertEquals("Dispatched",dispatched.label);assertEquals(c.infoContainer,dispatched.container)
        assertNotEquals(c.selection,draft.container);assertNotEquals(c.selection,dispatched.container)
        assertEquals("Read-only",serviceLoopStateStyle("READ_ONLY",c).label)
        assertEquals("Unavailable",serviceLoopStateStyle("DISABLED",c).label)
        assertEquals("Open",serviceLoopStateStyle("OPEN",c).label)
        assertEquals("Closed",serviceLoopStateStyle("CLOSED",c).label)
        assertEquals(ServiceLoopVisualState.History,serviceLoopStateStyle("CANCELED",c).state)
    }
}
