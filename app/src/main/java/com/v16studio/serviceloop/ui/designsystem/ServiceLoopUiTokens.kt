package com.v16studio.serviceloop.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ServiceLoopColorRoles(
    val canvas: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onAction: Color,
    val action: Color,
    val actionPressed: Color,
    val selection: Color,
    val onSelection: Color,
    val selectionOutline: Color,
    val outlineDecorative: Color,
    val outlineControl: Color,
    val icon: Color,
    val focus: Color,
    val disabledContainer: Color,
    val disabledText: Color,
    val neutralContainer: Color,
    val neutralInk: Color,
    val infoContainer: Color,
    val infoInk: Color,
    val warningContainer: Color,
    val warningInk: Color,
    val errorContainer: Color,
    val errorInk: Color,
    val destructive: Color,
    val onDestructive: Color,
    val successContainer: Color,
    val successInk: Color,
    val workingContainer: Color,
    val workingInk: Color,
    val historyContainer: Color,
    val historyInk: Color,
    val photoMat: Color,
    val scrimBase: Color,
)

object ServiceLoopUiTokens {
    val LightColors = ServiceLoopColorRoles(
        Color(0xFFF4F7F7), Color(0xFFFFFFFF), Color(0xFFEDF2F3), Color(0xFFFFFFFF),
        Color(0xFF182A30), Color(0xFF566870), Color(0xFF586D75), Color(0xFFFFFFFF),
        Color(0xFF08666B), Color(0xFF07565B), Color(0xFFE7F3F1), Color(0xFF075C62),
        Color(0xFF08747A), Color(0xFFD8E1E3), Color(0xFF6A828C), Color(0xFF566870),
        Color(0xFF005A61), Color(0xFFE6ECEE), Color(0xFF75858B), Color(0xFFEDF2F3),
        Color(0xFF425D67), Color(0xFFEAF0FA), Color(0xFF315F96), Color(0xFFFFF3DD),
        Color(0xFF875100), Color(0xFFFBE9E8), Color(0xFFA32D35), Color(0xFFA32D35),
        Color(0xFFFFFFFF), Color(0xFFE5F2EB), Color(0xFF236347), Color(0xFFE5F3F2),
        Color(0xFF07666B), Color(0xFFEEEAF4), Color(0xFF66517F), Color(0xFFE6ECEE),
        Color(0xFF000000),
    )
    val DarkColors = ServiceLoopColorRoles(
        Color(0xFF10191C), Color(0xFF19262B), Color(0xFF223239), Color(0xFF293C43),
        Color(0xFFEAF2F4), Color(0xFFB7C7CD), Color(0xFF9AAFBA), Color(0xFF062F32),
        Color(0xFF79D4CE), Color(0xFF69BDB8), Color(0xFF173C3D), Color(0xFFA4E6DF),
        Color(0xFF79D4CE), Color(0xFF344951), Color(0xFF809AA5), Color(0xFFB7C7CD),
        Color(0xFFA4E6DF), Color(0xFF2A383E), Color(0xFF82969F), Color(0xFF263A42),
        Color(0xFFD1E0E5), Color(0xFF20384F), Color(0xFFB8D7FF), Color(0xFF493519),
        Color(0xFFFFDA97), Color(0xFF49282D), Color(0xFFFFC0C4), Color(0xFFFFB3B9),
        Color(0xFF3B0A12), Color(0xFF173C30), Color(0xFFA5E3BF), Color(0xFF163B3E),
        Color(0xFFA0E4DF), Color(0xFF362E45), Color(0xFFD9C9F1), Color(0xFF0C1316),
        Color(0xFF000000),
    )

    object Space { val xs=4.dp; val sm=8.dp; val md=12.dp; val lg=16.dp; val xl=20.dp; val section=24.dp; val major=32.dp }
    object Radius { val badge=8.dp; val field=12.dp; val card=16.dp; val dialog=24.dp; val pill=999.dp }
    object Stroke { val divider=1.dp; val outline=1.dp; val selected=1.5.dp; val focus=2.dp }
    object Size { val touchMin=48.dp; val buttonMin=48.dp; val buttonPrimaryMin=52.dp; val fieldMin=64.dp; val topBarMin=64.dp; val listRowMin=64.dp }
    object Layout { val pageInsetCompact=16.dp; val bodyGap=12.dp; val sectionGap=24.dp; val cardPadding=16.dp; val barPadding=16.dp; val pairGap=8.dp }

    val Typography = Typography(
        headlineSmall = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.SemiBold, fontSize=18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.SemiBold, fontSize=16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Normal, fontSize=16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Normal, fontSize=14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Normal, fontSize=12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.SemiBold, fontSize=14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.SemiBold, fontSize=12.sp, lineHeight = 16.sp),
    )
    val IdentifierStyle = TextStyle(fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Medium, fontSize=14.sp, lineHeight = 20.sp)
    val DayHeadingStyle = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=12.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp)
    val HeroCountStyle = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=32.sp, lineHeight = 40.sp)
    val Shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.badge),
        small = RoundedCornerShape(Radius.field),
        medium = RoundedCornerShape(Radius.card),
        large = RoundedCornerShape(Radius.dialog),
        extraLarge = RoundedCornerShape(Radius.dialog),
    )
}

enum class ServiceLoopVisualState { Neutral, Info, Warning, Error, Success, Working, History }

data class ServiceLoopStateStyle(val label: String, val container: Color, val ink: Color, val state: ServiceLoopVisualState)

fun serviceLoopStateStyle(code: String, colors: ServiceLoopColorRoles): ServiceLoopStateStyle {
    val normalized = code.trim().uppercase()
    val (label, family) = when (normalized) {
        "DRAFT" -> "Draft" to ServiceLoopVisualState.Warning
        "DISPATCHED" -> "Dispatched" to ServiceLoopVisualState.Info
        "CONCLUDED" -> "Concluded" to ServiceLoopVisualState.Neutral
        "BOOKED" -> "Booked" to ServiceLoopVisualState.Info
        "WORKING" -> "Working" to ServiceLoopVisualState.Working
        "SAVED" -> "Saved on this device" to ServiceLoopVisualState.Success
        "FAILED" -> "Not saved — action needed" to ServiceLoopVisualState.Error
        "ISSUE_FOUND" -> "Issue found" to ServiceLoopVisualState.Warning
        "NOT_APPLICABLE" -> "Not applicable" to ServiceLoopVisualState.Neutral
        "VALUE" -> "Value recorded" to ServiceLoopVisualState.Neutral
        "REVIEWED" -> "Reviewed" to ServiceLoopVisualState.Success
        "READ_ONLY" -> "Read-only" to ServiceLoopVisualState.Neutral
        "DISABLED" -> "Unavailable" to ServiceLoopVisualState.Neutral
        else -> code.lowercase().replaceFirstChar { it.uppercase() } to ServiceLoopVisualState.Neutral
    }
    val pair = when (family) {
        ServiceLoopVisualState.Neutral -> colors.neutralContainer to colors.neutralInk
        ServiceLoopVisualState.Info -> colors.infoContainer to colors.infoInk
        ServiceLoopVisualState.Warning -> colors.warningContainer to colors.warningInk
        ServiceLoopVisualState.Error -> colors.errorContainer to colors.errorInk
        ServiceLoopVisualState.Success -> colors.successContainer to colors.successInk
        ServiceLoopVisualState.Working -> colors.workingContainer to colors.workingInk
        ServiceLoopVisualState.History -> colors.historyContainer to colors.historyInk
    }
    return ServiceLoopStateStyle(label, pair.first, pair.second, family)
}
