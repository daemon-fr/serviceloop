package com.v16studio.serviceloop.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.CubicBezierEasing
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
    val brand: Color,
    val recordBorder: Color,
    val tonalCommandContainer: Color,
    val tonalCommandInk: Color,
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
        Color(0xFF000000), Color(0xFF08747A), Color(0xFF67B9B8), Color(0xFFD7EFED), Color(0xFF075C62),
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
        Color(0xFF000000), Color(0xFF08747A), Color(0xFF4D8D8A), Color(0xFF234B4D), Color(0xFFA4E6DF),
    )

    object Space { val none=0.dp; val hair=2.dp; val xs=4.dp; val sm=8.dp; val md=12.dp; val lg=16.dp; val xl=20.dp; val section=24.dp; val major=32.dp; val hero=40.dp; val large=48.dp }
    object Radius { val badge=8.dp; val field=12.dp; val card=16.dp; val dialog=24.dp; val pill=999.dp }
    object Stroke { val divider=1.dp; val outline=1.dp; val selected=1.5.dp; val focus=2.dp; val record=2.dp }
    object Size { val touchMin=48.dp; val iconSmall=20.dp; val icon=24.dp; val iconLarge=32.dp; val checkboxGlyph=24.dp; val buttonMin=48.dp; val buttonPrimaryMin=52.dp; val fieldMin=64.dp; val pickerMin=64.dp; val topBarMin=64.dp; val brandMin=28.dp; val bottomNavMin=80.dp; val listRowMin=64.dp; val photoThumb=64.dp; val photoGridMin=88.dp; val editorActionReserve=56.dp; val sheetMaxWidth=560.dp; val dialogMaxWidth=560.dp; val menuMinWidth=196.dp; val menuMaxWidth=320.dp; val formMaxWidth=640.dp; val contentMaxWidth=840.dp; val narrowThreshold=360.dp; val mediumThreshold=600.dp; val expandedThreshold=840.dp; val compactHeightThreshold=480.dp; val pairMinCellWidth=148.dp }
    object Layout { val pageInsetCompact=16.dp; val pageInsetMedium=24.dp; val pageInsetExpanded=32.dp; val bodyGap=12.dp; val sectionGap=24.dp; val buttonGap=8.dp; val cardPadding=16.dp; val barPadding=16.dp; val pairGap=8.dp; const val fontScaleStackThreshold=1.3f; const val badgeFontScaleStackThreshold=1.5f; val bodyMinimumVisibleHeight=160.dp }
    object Motion { const val pressMs=80; const val smallMs=120; const val containerMs=180; const val dialogMs=180; const val routeMs=0; const val reducedMs=0; val easing=CubicBezierEasing(.2f,0f,0f,1f) }
    object Elevation { val rest=0.dp; val menu=3.dp; val dialog=6.dp; val bottomBar=0.dp }
    object Alpha { const val pressed=.08f; const val hover=.04f; const val scrimLight=.4f; const val scrimDark=.64f }

    object Type {
        private fun sans(size:Int,line:Int,weight:FontWeight,spacing:Float=0f)=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=weight,fontSize=size.sp,lineHeight=line.sp,letterSpacing=spacing.sp)
        val screenTitle=sans(22,28,FontWeight.Bold)
        val heroCount=sans(32,40,FontWeight.Bold)
        val sectionTitle=sans(18,24,FontWeight.SemiBold)
        val itemTitle=sans(16,22,FontWeight.SemiBold)
        val body=sans(16,24,FontWeight.Normal)
        val supporting=sans(14,20,FontWeight.Normal)
        val label=sans(14,20,FontWeight.SemiBold)
        val meta=sans(12,18,FontWeight.Normal)
        val dayHeading=sans(12,18,FontWeight.Bold,.6f)
        val button=sans(16,22,FontWeight.SemiBold)
        val identifier=TextStyle(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp,letterSpacing=0.sp)
        val badge=sans(12,16,FontWeight.SemiBold)
    }

    val Typography = Typography(
        headlineSmall=Type.screenTitle,titleLarge=Type.sectionTitle,titleMedium=Type.itemTitle,bodyLarge=Type.body,
        bodyMedium=Type.supporting,bodySmall=Type.meta,labelLarge=Type.label,labelMedium=Type.badge,
    )
    val IdentifierStyle = Type.identifier
    val DayHeadingStyle = Type.dayHeading
    val HeroCountStyle = Type.heroCount
    val Shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.badge),
        small = RoundedCornerShape(Radius.field),
        medium = RoundedCornerShape(Radius.card),
        large = RoundedCornerShape(Radius.dialog),
        extraLarge = RoundedCornerShape(Radius.dialog),
    )
}

object ServiceLoopButtonContract {
    val radius = ServiceLoopUiTokens.Radius.field
    val horizontalPadding = ServiceLoopUiTokens.Space.xl
    val verticalPadding = ServiceLoopUiTokens.Space.md
    val primaryMinHeight = ServiceLoopUiTokens.Size.buttonPrimaryMin
    val secondaryMinHeight = ServiceLoopUiTokens.Size.buttonMin
    val focusWidth = ServiceLoopUiTokens.Stroke.focus
    val focusGap = ServiceLoopUiTokens.Space.hair
    val textStyle = ServiceLoopUiTokens.Type.button
    fun primaryContainer(colors:ServiceLoopColorRoles,enabled:Boolean,pressed:Boolean)=if(!enabled)colors.disabledContainer else if(pressed)colors.actionPressed else colors.action
    fun primaryInk(colors:ServiceLoopColorRoles,enabled:Boolean)=if(enabled)colors.onAction else colors.disabledText
    fun secondaryContainer(colors:ServiceLoopColorRoles,enabled:Boolean)=if(enabled)colors.tonalCommandContainer else colors.disabledContainer
    fun secondaryInk(colors:ServiceLoopColorRoles,enabled:Boolean)=if(enabled)colors.tonalCommandInk else colors.disabledText
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
        "OPEN" -> "Open" to ServiceLoopVisualState.Info
        "CLOSED" -> "Closed" to ServiceLoopVisualState.Neutral
        "ACTIVE" -> "Active" to ServiceLoopVisualState.Success
        "PAUSED" -> "Paused" to ServiceLoopVisualState.Warning
        "ENDED" -> "Ended" to ServiceLoopVisualState.History
        "CANCELED" -> "Canceled" to ServiceLoopVisualState.History
        "COMPLETED" -> "Completed" to ServiceLoopVisualState.Success
        "ARCHIVED" -> "Archived" to ServiceLoopVisualState.History
        "RETIRED" -> "Retired" to ServiceLoopVisualState.History
        "SAVED" -> "Saved on this device" to ServiceLoopVisualState.Success
        "FAILED" -> "Not saved — action needed" to ServiceLoopVisualState.Error
        "ISSUE_FOUND" -> "Issue found" to ServiceLoopVisualState.Warning
        "NOT_APPLICABLE" -> "Not applicable" to ServiceLoopVisualState.Neutral
        "VALUE" -> "Value recorded" to ServiceLoopVisualState.Neutral
        "REVIEWED" -> "Reviewed" to ServiceLoopVisualState.Success
        "READ_ONLY" -> "Read-only" to ServiceLoopVisualState.Neutral
        "DISABLED" -> "Unavailable" to ServiceLoopVisualState.Neutral
        else -> "State unavailable" to ServiceLoopVisualState.Error
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
