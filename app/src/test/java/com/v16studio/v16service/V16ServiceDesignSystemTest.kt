package com.v16studio.v16service

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import com.v16studio.v16service.ui.designsystem.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V16ServiceDesignSystemTest {
    @Test fun filterSelectorOutlineUsesPaleTealLightAndVisibleTealDark() {
        assertEquals(V16ServiceUiTokens.LightColors.recordBorder, v16ServiceFilterSelectorOutline(V16ServiceUiTokens.LightColors))
        assertEquals(V16ServiceUiTokens.DarkColors.selectionOutline, v16ServiceFilterSelectorOutline(V16ServiceUiTokens.DarkColors))
        assertNotEquals(V16ServiceUiTokens.LightColors.outlineControl, v16ServiceFilterSelectorOutline(V16ServiceUiTokens.LightColors))
    }

    @Test fun brandStripWordmarkColorsHaveReadableContrastInBothThemes() {
        fun contrast(foreground:Color,background:Color):Float {
            val high=maxOf(foreground.luminance(),background.luminance())
            val low=minOf(foreground.luminance(),background.luminance())
            return (high+.05f)/(low+.05f)
        }
        assertEquals(true,contrast(V16ServiceUiTokens.LightColors.textPrimary,V16ServiceUiTokens.LightColors.brandBand)>=4.5f)
        assertEquals(true,contrast(V16ServiceUiTokens.LightColors.action,V16ServiceUiTokens.LightColors.brandBand)>=4.5f)
        assertEquals(true,contrast(V16ServiceUiTokens.DarkColors.textPrimary,V16ServiceUiTokens.DarkColors.brandBand)>=4.5f)
        assertEquals(true,contrast(V16ServiceUiTokens.DarkColors.action,V16ServiceUiTokens.DarkColors.brandBand)>=4.5f)
        assertEquals(Color.Black, v16ServiceBrandNeutral(V16ServiceUiTokens.LightColors))
        assertEquals(Color.White, v16ServiceBrandNeutral(V16ServiceUiTokens.DarkColors))
    }
    @Test fun canonicalLightAndDarkRolesAreExact() {
        with(V16ServiceUiTokens.LightColors) {
            assertEquals(Color(0xFFF4F7F7), canvas); assertEquals(Color(0xFFFFFFFF), surface)
            assertEquals(Color(0xFF182A30), textPrimary); assertEquals(Color(0xFF08666B), action)
            assertEquals(Color(0xFFE7F3F1), selection); assertEquals(Color(0xFF08747A), selectionOutline)
            assertEquals(Color(0xFF6A828C), outlineControl); assertEquals(Color(0xFFA32D35), errorInk)
            assertEquals(Color(0xFF182A30), selectionInk)
        }
        with(V16ServiceUiTokens.DarkColors) {
            assertEquals(Color(0xFF10191C), canvas); assertEquals(Color(0xFF19262B), surface)
            assertEquals(Color(0xFFEAF2F4), textPrimary); assertEquals(Color(0xFF79D4CE), action)
            assertEquals(Color(0xFF173C3D), selection); assertEquals(Color(0xFF79D4CE), selectionOutline)
            assertEquals(Color(0xFF809AA5), outlineControl); assertEquals(Color(0xFFFFC0C4), errorInk)
            assertEquals(Color.White, selectionInk)
        }
    }

    @Test fun allFortyOneCustomRolesExistInBothAppearances() {
        fun roles(c:V16ServiceColorRoles)=listOf(c.canvas,c.surface,c.surfaceSubtle,c.surfaceRaised,c.textPrimary,c.textSecondary,c.textMuted,c.onAction,c.action,c.actionPressed,c.selection,c.onSelection,c.selectionOutline,c.outlineDecorative,c.outlineControl,c.icon,c.focus,c.disabledContainer,c.disabledText,c.neutralContainer,c.neutralInk,c.infoContainer,c.infoInk,c.warningContainer,c.warningInk,c.errorContainer,c.errorInk,c.destructive,c.onDestructive,c.successContainer,c.successInk,c.workingContainer,c.workingInk,c.historyContainer,c.historyInk,c.photoMat,c.scrimBase,c.brandBand,c.recordBorder,c.tonalCommandContainer,c.tonalCommandInk)
        val light=listOf(0xFFF4F7F7,0xFFFFFFFF,0xFFEDF2F3,0xFFFFFFFF,0xFF182A30,0xFF566870,0xFF586D75,0xFFFFFFFF,0xFF08666B,0xFF07565B,0xFFE7F3F1,0xFF075C62,0xFF08747A,0xFFD8E1E3,0xFF6A828C,0xFF566870,0xFF005A61,0xFFE6ECEE,0xFF75858B,0xFFEDF2F3,0xFF425D67,0xFFEAF0FA,0xFF315F96,0xFFFFF3DD,0xFF875100,0xFFFBE9E8,0xFFA32D35,0xFFA32D35,0xFFFFFFFF,0xFFE5F2EB,0xFF236347,0xFFE5F3F2,0xFF07666B,0xFFEEEAF4,0xFF66517F,0xFFE6ECEE,0xFF000000,0xFFF0F8F7,0xFF67B9B8,0xFFD7EFED,0xFF075C62).map(::Color)
        val dark=listOf(0xFF10191C,0xFF19262B,0xFF223239,0xFF293C43,0xFFEAF2F4,0xFFB7C7CD,0xFF9AAFBA,0xFF062F32,0xFF79D4CE,0xFF69BDB8,0xFF173C3D,0xFFA4E6DF,0xFF79D4CE,0xFF344951,0xFF809AA5,0xFFB7C7CD,0xFFA4E6DF,0xFF2A383E,0xFF82969F,0xFF263A42,0xFFD1E0E5,0xFF20384F,0xFFB8D7FF,0xFF493519,0xFFFFDA97,0xFF49282D,0xFFFFC0C4,0xFFFFB3B9,0xFF3B0A12,0xFF173C30,0xFFA5E3BF,0xFF163B3E,0xFFA0E4DF,0xFF362E45,0xFFD9C9F1,0xFF0C1316,0xFF000000,0xFF152629,0xFF4D8D8A,0xFF234B4D,0xFFA4E6DF).map(::Color)
        assertEquals(light,roles(V16ServiceUiTokens.LightColors))
        assertEquals(dark,roles(V16ServiceUiTokens.DarkColors))
    }

    @Test fun materialMappingIsExplicitForContainerInverseErrorAndFixedRoles() {
        fun assertComplete(c:V16ServiceColorRoles,dark:Boolean) {
            val scheme=if(dark)v16ServiceDarkColorScheme() else v16ServiceLightColorScheme()
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
        assertComplete(V16ServiceUiTokens.LightColors,false)
        assertComplete(V16ServiceUiTokens.DarkColors,true)
    }

    @Test fun typographyAndGeometryMatchCanonicalTokens() {
        fun dp(expected:Float,actual:androidx.compose.ui.unit.Dp)=assertEquals(expected,actual.value,0f)
        listOf(0f,2f,4f,8f,12f,16f,20f,24f,32f,40f,48f).zip(listOf(V16ServiceUiTokens.Space.none,V16ServiceUiTokens.Space.hair,V16ServiceUiTokens.Space.xs,V16ServiceUiTokens.Space.sm,V16ServiceUiTokens.Space.md,V16ServiceUiTokens.Space.lg,V16ServiceUiTokens.Space.xl,V16ServiceUiTokens.Space.section,V16ServiceUiTokens.Space.major,V16ServiceUiTokens.Space.hero,V16ServiceUiTokens.Space.large)).forEach{dp(it.first,it.second)}
        listOf(8f,12f,16f,24f,999f).zip(listOf(V16ServiceUiTokens.Radius.badge,V16ServiceUiTokens.Radius.field,V16ServiceUiTokens.Radius.card,V16ServiceUiTokens.Radius.dialog,V16ServiceUiTokens.Radius.pill)).forEach{dp(it.first,it.second)}
        listOf(1f,1f,1.5f,2f,2f).zip(listOf(V16ServiceUiTokens.Stroke.divider,V16ServiceUiTokens.Stroke.outline,V16ServiceUiTokens.Stroke.selected,V16ServiceUiTokens.Stroke.focus,V16ServiceUiTokens.Stroke.record)).forEach{dp(it.first,it.second)}
        val sizes=listOf(48f,20f,24f,32f,24f,48f,52f,64f,64f,64f,36f,80f,64f,64f,88f,56f,560f,560f,196f,320f,640f,840f,360f,600f,840f,480f,148f)
        val actualSizes=listOf(V16ServiceUiTokens.Size.touchMin,V16ServiceUiTokens.Size.iconSmall,V16ServiceUiTokens.Size.icon,V16ServiceUiTokens.Size.iconLarge,V16ServiceUiTokens.Size.checkboxGlyph,V16ServiceUiTokens.Size.buttonMin,V16ServiceUiTokens.Size.buttonPrimaryMin,V16ServiceUiTokens.Size.fieldMin,V16ServiceUiTokens.Size.pickerMin,V16ServiceUiTokens.Size.topBarMin,V16ServiceUiTokens.Size.brandMin,V16ServiceUiTokens.Size.bottomNavMin,V16ServiceUiTokens.Size.listRowMin,V16ServiceUiTokens.Size.photoThumb,V16ServiceUiTokens.Size.photoGridMin,V16ServiceUiTokens.Size.editorActionReserve,V16ServiceUiTokens.Size.sheetMaxWidth,V16ServiceUiTokens.Size.dialogMaxWidth,V16ServiceUiTokens.Size.menuMinWidth,V16ServiceUiTokens.Size.menuMaxWidth,V16ServiceUiTokens.Size.formMaxWidth,V16ServiceUiTokens.Size.contentMaxWidth,V16ServiceUiTokens.Size.narrowThreshold,V16ServiceUiTokens.Size.mediumThreshold,V16ServiceUiTokens.Size.expandedThreshold,V16ServiceUiTokens.Size.compactHeightThreshold,V16ServiceUiTokens.Size.pairMinCellWidth)
        sizes.zip(actualSizes).forEach{dp(it.first,it.second)}
        val layouts=listOf(16f,24f,32f,12f,24f,8f,16f,16f,8f,160f)
        val actualLayouts=listOf(V16ServiceUiTokens.Layout.pageInsetCompact,V16ServiceUiTokens.Layout.pageInsetMedium,V16ServiceUiTokens.Layout.pageInsetExpanded,V16ServiceUiTokens.Layout.bodyGap,V16ServiceUiTokens.Layout.sectionGap,V16ServiceUiTokens.Layout.buttonGap,V16ServiceUiTokens.Layout.cardPadding,V16ServiceUiTokens.Layout.barPadding,V16ServiceUiTokens.Layout.pairGap,V16ServiceUiTokens.Layout.bodyMinimumVisibleHeight)
        layouts.zip(actualLayouts).forEach{dp(it.first,it.second)}
        assertEquals(1.3f,V16ServiceUiTokens.Layout.fontScaleStackThreshold,0f);assertEquals(1.5f,V16ServiceUiTokens.Layout.badgeFontScaleStackThreshold,0f)
        assertEquals(listOf(80,120,180,180,0,0),listOf(V16ServiceUiTokens.Motion.pressMs,V16ServiceUiTokens.Motion.smallMs,V16ServiceUiTokens.Motion.containerMs,V16ServiceUiTokens.Motion.dialogMs,V16ServiceUiTokens.Motion.routeMs,V16ServiceUiTokens.Motion.reducedMs))
        assertEquals(androidx.compose.animation.core.CubicBezierEasing(.2f,0f,0f,1f).transform(.5f),V16ServiceUiTokens.Motion.easing.transform(.5f),.0001f)
        listOf(0f,3f,6f,0f).zip(listOf(V16ServiceUiTokens.Elevation.rest,V16ServiceUiTokens.Elevation.menu,V16ServiceUiTokens.Elevation.dialog,V16ServiceUiTokens.Elevation.bottomBar)).forEach{dp(it.first,it.second)}
        assertEquals(listOf(.08f,.04f,.4f,.64f),listOf(V16ServiceUiTokens.Alpha.pressed,V16ServiceUiTokens.Alpha.hover,V16ServiceUiTokens.Alpha.scrimLight,V16ServiceUiTokens.Alpha.scrimDark))
        fun type(style:androidx.compose.ui.text.TextStyle,size:Float,line:Float,weight:androidx.compose.ui.text.font.FontWeight,spacing:Float=0f){assertEquals(size,style.fontSize.value,0f);assertEquals(line,style.lineHeight.value,0f);assertEquals(weight,style.fontWeight);assertEquals(spacing,style.letterSpacing.value,0f)}
        type(V16ServiceUiTokens.Type.screenTitle,22f,28f,androidx.compose.ui.text.font.FontWeight.Bold);type(V16ServiceUiTokens.Type.heroCount,32f,40f,androidx.compose.ui.text.font.FontWeight.Bold);type(V16ServiceUiTokens.Type.sectionTitle,18f,24f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(V16ServiceUiTokens.Type.itemTitle,16f,22f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(V16ServiceUiTokens.Type.body,16f,24f,androidx.compose.ui.text.font.FontWeight.Normal);type(V16ServiceUiTokens.Type.supporting,14f,20f,androidx.compose.ui.text.font.FontWeight.Normal);type(V16ServiceUiTokens.Type.label,14f,20f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(V16ServiceUiTokens.Type.meta,12f,18f,androidx.compose.ui.text.font.FontWeight.Normal);type(V16ServiceUiTokens.Type.dayHeading,12f,18f,androidx.compose.ui.text.font.FontWeight.Bold,.6f);type(V16ServiceUiTokens.Type.button,16f,22f,androidx.compose.ui.text.font.FontWeight.SemiBold);type(V16ServiceUiTokens.Type.identifier,14f,20f,androidx.compose.ui.text.font.FontWeight.Medium);type(V16ServiceUiTokens.Type.badge,12f,16f,androidx.compose.ui.text.font.FontWeight.SemiBold)
    }

    @Test fun canonicalButtonContractUsesExactStateFamilies() {
        val c=V16ServiceUiTokens.LightColors
        assertEquals(12f,V16ServiceButtonContract.radius.value,0f);assertEquals(20f,V16ServiceButtonContract.horizontalPadding.value,0f);assertEquals(12f,V16ServiceButtonContract.verticalPadding.value,0f)
        assertEquals(52f,V16ServiceButtonContract.primaryMinHeight.value,0f);assertEquals(48f,V16ServiceButtonContract.secondaryMinHeight.value,0f);assertEquals(2f,V16ServiceButtonContract.focusWidth.value,0f);assertEquals(2f,V16ServiceButtonContract.focusGap.value,0f)
        assertEquals(c.action,V16ServiceButtonContract.primaryContainer(c,true,false));assertEquals(c.actionPressed,V16ServiceButtonContract.primaryContainer(c,true,true));assertEquals(c.disabledContainer,V16ServiceButtonContract.primaryContainer(c,false,false));assertEquals(c.onAction,V16ServiceButtonContract.primaryInk(c,true));assertEquals(c.disabledText,V16ServiceButtonContract.primaryInk(c,false))
        assertEquals(c.tonalCommandContainer,V16ServiceButtonContract.secondaryContainer(c,true));assertEquals(c.tonalCommandInk,V16ServiceButtonContract.secondaryInk(c,true));assertEquals(c.disabledContainer,V16ServiceButtonContract.secondaryContainer(c,false));assertEquals(c.disabledText,V16ServiceButtonContract.secondaryInk(c,false));assertEquals(V16ServiceUiTokens.Type.button,V16ServiceButtonContract.textStyle)
    }

    @Test fun richButtonAdaptersDelegateToTheCanonicalContentPrimitive() {
        val source = productionKotlinSourceContaining("= V16ServiceButtonContent(")
        assertEquals(2,Regex("= V16ServiceButtonContent\\(").findAll(source).count())
        assertEquals(false,source.contains("= androidx.compose.material3.Button("))
        assertEquals(false,source.contains("= androidx.compose.material3.OutlinedButton("))
        assertEquals(V16ServiceUiTokens.Type.button,V16ServiceButtonContract.textStyle)
        assertEquals(52f,V16ServiceButtonContract.primaryMinHeight.value,0f)
        assertEquals(48f,V16ServiceButtonContract.secondaryMinHeight.value,0f)
    }

    @Test fun b032TemplateToolsUseCircularIconOnlyActionsWithExactSemantics() {
        val actions = productionKotlinSourceContaining("fun V16ServiceIconOnlyAction")
        val template = productionKotlinSourceContaining("primaryAccessibleName = \"Save item changes\"")
        assertTrue(actions.contains(".size(56.dp)"))
        assertTrue(actions.contains(".clip(CircleShape)"))
        assertTrue(actions.contains("if (!enabled) disabled()"))
        assertTrue(template.contains("V16ServiceIcons.Delete"))
        assertTrue(template.contains("V16ServiceIcons.ArrowFatLineUp"))
        assertTrue(template.contains("V16ServiceIcons.ArrowFatLineDown"))
        assertTrue(template.contains("V16ServiceIcons.CheckFat"))
        assertTrue(template.contains("V16ServiceIcons.PencilSimple"))
        assertFalse(template.contains("V16ServiceCompactIconLabelAction("))
    }

    @Test fun b032TemplateIconManifestUsesTheRequestedPhosphorFillAssets() {
        val workingDirectory = java.io.File(requireNotNull(System.getProperty("user.dir")))
        val repositoryRoot = if (workingDirectory.name == "app") workingDirectory.parentFile else workingDirectory
        val manifest = repositoryRoot.resolve("tools/phosphor/v16service-icons.json").readText()
        assertTrue(manifest.contains("\"ArrowFatLineUp\": \"arrow-fat-line-up\""))
        assertTrue(manifest.contains("\"ArrowFatLineDown\": \"arrow-fat-line-down\""))
        assertTrue(manifest.contains("\"CheckFat\": \"check-fat\""))
        assertTrue(manifest.contains("\"PencilSimple\": \"pencil-simple\""))
        assertTrue(manifest.contains("\"Delete\": \"trash\""))
    }

    @Test fun b031RegisterAndSearchKeepTheirFixedProductOrder() {
        val directory = productionKotlinSourceContaining("V16ServiceRootSecondaryTabs(listOf(\"CUSTOMERS\"")
        val search = productionKotlinSourceContaining("private enum class SearchCategory")
        assertTrue(directory.contains("\"TEMPLATES\" to \"Templates\""))
        assertTrue(search.indexOf("CUSTOMER(\"CUSTOMER\"") < search.indexOf("SITE(\"SITE\"") )
        assertTrue(search.indexOf("TEMPLATE(\"TEMPLATE\"") < search.indexOf("PLAN(\"PLAN\"") )
        assertTrue(search.indexOf("PLAN(\"PLAN\"") < search.indexOf("VISIT(\"VISIT\"") )
    }

    @Test fun ownerR1SharedVisualContractsStayStructural() {
        val components = productionKotlinSourceContaining("maxIntrinsicWidth")
        val app = productionKotlinSourceContaining("DetailScaffold(\"Service\", nav)")
        val daily = productionKotlinSourceContaining("V16ServiceEntityRecord", "V16ServiceSectionDivider")
        val rootScaffold = productionKotlinFunctionSource("internal fun RootScaffold")
        assertEquals(true,components.contains("maxIntrinsicWidth"))
        assertEquals(false,components.contains("label.length"))
        assertEquals(true,components.contains("PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))"))
        assertEquals(true,components.contains("Saved · \$time"))
        assertEquals(false,components.contains("Saved on this device"))
        assertEquals(true,components.contains("softWrap = false"))
        assertEquals(false,app.contains("title.contains("))
        assertEquals(true,app.contains("DetailScaffold(\"Service\", nav)"))
        assertEquals(true,components.contains("maxOf(leftWidthPx, rightWidthPx)"))
        assertEquals(true,rootScaffold.contains("V16ServiceBrandStrip()"))
        assertEquals(true,productionKotlinSourceContaining("V16ServiceContentTabs").isNotEmpty())
        assertEquals(true,daily.contains("V16ServiceEntityRecord"))
        assertEquals(true,daily.contains("V16ServiceSectionDivider"))
    }

    @Test fun c07AdapterContractsAreCanonical() {
        assertEquals(14f,V16ServiceUiTokens.Type.label.fontSize.value,0f)
        assertEquals(20f,V16ServiceUiTokens.Type.label.lineHeight.value,0f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold,V16ServiceUiTokens.Type.label.fontWeight)
        assertEquals(24f,V16ServiceUiTokens.Size.icon.value,0f)
        assertEquals(48f,V16ServiceUiTokens.Size.touchMin.value,0f)
    }

    @Test fun responsivePageInsetsUseCanonicalThresholds() {
        assertEquals(16f, v16ServicePageInset(320.dp).value, 0f)
        assertEquals(16f, v16ServicePageInset(599.dp).value, 0f)
        assertEquals(24f, v16ServicePageInset(600.dp).value, 0f)
        assertEquals(24f, v16ServicePageInset(839.dp).value, 0f)
        assertEquals(32f, v16ServicePageInset(840.dp).value, 0f)
    }

    @Test fun scaffoldPaddingAddsOnlyTheAdaptiveInsetDelta() {
        val base = PaddingValues(top = 64.dp, bottom = 80.dp)
        val medium = v16ServiceAdaptiveScaffoldPadding(base, 700.dp, LayoutDirection.Ltr)
        val expanded = v16ServiceAdaptiveScaffoldPadding(base, 900.dp, LayoutDirection.Ltr)
        assertEquals(8f, medium.calculateLeftPadding(LayoutDirection.Ltr).value, 0f)
        assertEquals(16f, expanded.calculateLeftPadding(LayoutDirection.Ltr).value, 0f)
        assertEquals(64f, expanded.calculateTopPadding().value, 0f)
        assertEquals(80f, expanded.calculateBottomPadding().value, 0f)
    }

    @Test fun unknownStateIsUnavailableAndUsesErrorFamily() {
        val c=V16ServiceUiTokens.LightColors;val state=v16ServiceStateStyle("SOMETHING_NEW",c)
        assertEquals("State unavailable",state.label);assertEquals(V16ServiceVisualState.Error,state.state);assertEquals(c.errorContainer,state.container);assertEquals(c.errorInk,state.ink)
    }

    @Test fun selectionAndLifecycleStatusRemainIndependent() {
        val c=V16ServiceUiTokens.LightColors
        val draft=v16ServiceStateStyle("DRAFT",c);val dispatched=v16ServiceStateStyle("DISPATCHED",c)
        assertEquals("Draft",draft.label);assertEquals(c.warningContainer,draft.container)
        assertEquals("Dispatched",dispatched.label);assertEquals(c.infoContainer,dispatched.container)
        assertNotEquals(c.selection,draft.container);assertNotEquals(c.selection,dispatched.container)
        assertEquals("Read-only",v16ServiceStateStyle("READ_ONLY",c).label)
        assertEquals("Disabled",v16ServiceStateStyle("DISABLED",c).label)
        assertEquals("Open",v16ServiceStateStyle("OPEN",c).label)
        assertEquals("Closed",v16ServiceStateStyle("CLOSED",c).label)
        assertEquals(V16ServiceVisualState.History,v16ServiceStateStyle("CANCELED",c).state)
    }

    @Test fun longTextEditorUsesLocalClearanceAndKeepsTheExpandTargetCompact() {
        val source = productionKotlinFunctionSource("fun V16ServiceLongTextEditor", "fun Modifier.v16ServiceFocusRing")
        assertFalse(source.contains("suffix = { Spacer(Modifier.width(V16ServiceUiTokens.Size.editorActionReserve)) }"))
        assertTrue(source.contains("contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 52.dp)"))
        assertTrue(source.contains("Modifier.size(V16ServiceUiTokens.Size.iconSmall)"))
        assertTrue(source.contains("contentAlignment = Alignment.BottomEnd"))
        assertTrue(source.contains("padding(end = 4.dp, bottom = 4.dp)"))
        assertEquals(48f, V16ServiceUiTokens.Size.touchMin.value, 0f)
        assertEquals(20f, V16ServiceUiTokens.Size.iconSmall.value, 0f)
    }
}
