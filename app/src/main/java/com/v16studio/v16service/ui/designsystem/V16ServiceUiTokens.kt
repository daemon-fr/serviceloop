package com.v16studio.v16service.ui.designsystem

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

data class V16ServiceColorRoles(
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
    val brandBand: Color,
    val recordBorder: Color,
    val tonalCommandContainer: Color,
    val tonalCommandInk: Color,
    /** Ink for a selected value option; deliberately distinct from command-button ink. */
    val selectionInk: Color = Color.Unspecified,
)

object V16ServiceUiTokens {
    val LightColors = V16ServiceColorRoles(
        Color(0xFFF4F7F7), Color(0xFFFFFFFF), Color(0xFFEDF2F3), Color(0xFFFFFFFF),
        Color(0xFF182A30), Color(0xFF566870), Color(0xFF586D75), Color(0xFFFFFFFF),
        Color(0xFF08666B), Color(0xFF07565B), Color(0xFFE7F3F1), Color(0xFF075C62),
        Color(0xFF08747A), Color(0xFFD8E1E3), Color(0xFF6A828C), Color(0xFF566870),
        Color(0xFF005A61), Color(0xFFE6ECEE), Color(0xFF75858B), Color(0xFFEDF2F3),
        Color(0xFF425D67), Color(0xFFEAF0FA), Color(0xFF315F96), Color(0xFFFFF3DD),
        Color(0xFF875100), Color(0xFFFBE9E8), Color(0xFFA32D35), Color(0xFFA32D35),
        Color(0xFFFFFFFF), Color(0xFFE5F2EB), Color(0xFF236347), Color(0xFFE5F3F2),
        Color(0xFF07666B), Color(0xFFEEEAF4), Color(0xFF66517F), Color(0xFFE6ECEE),
        Color(0xFF000000), Color(0xFFF0F8F7), Color(0xFF67B9B8), Color(0xFFD7EFED), Color(0xFF075C62), Color(0xFF182A30),
    )
    val DarkColors = V16ServiceColorRoles(
        Color(0xFF10191C), Color(0xFF19262B), Color(0xFF223239), Color(0xFF293C43),
        Color(0xFFEAF2F4), Color(0xFFB7C7CD), Color(0xFF9AAFBA), Color(0xFF062F32),
        Color(0xFF79D4CE), Color(0xFF69BDB8), Color(0xFF173C3D), Color(0xFFA4E6DF),
        Color(0xFF79D4CE), Color(0xFF344951), Color(0xFF809AA5), Color(0xFFB7C7CD),
        Color(0xFFA4E6DF), Color(0xFF2A383E), Color(0xFF82969F), Color(0xFF263A42),
        Color(0xFFD1E0E5), Color(0xFF20384F), Color(0xFFB8D7FF), Color(0xFF493519),
        Color(0xFFFFDA97), Color(0xFF49282D), Color(0xFFFFC0C4), Color(0xFFFFB3B9),
        Color(0xFF3B0A12), Color(0xFF173C30), Color(0xFFA5E3BF), Color(0xFF163B3E),
        Color(0xFFA0E4DF), Color(0xFF362E45), Color(0xFFD9C9F1), Color(0xFF0C1316),
        Color(0xFF000000), Color(0xFF152629), Color(0xFF4D8D8A), Color(0xFF234B4D), Color(0xFFA4E6DF), Color.White,
    )

    object Space { val none=0.dp; val hair=2.dp; val xs=4.dp; val sm=8.dp; val md=12.dp; val lg=16.dp; val xl=20.dp; val section=24.dp; val major=32.dp; val hero=40.dp; val large=48.dp }
    object Radius { val badge=8.dp; val field=12.dp; val card=16.dp; val dialog=24.dp; val pill=999.dp }
    object Stroke { val divider=1.dp; val outline=1.dp; val selected=1.5.dp; val tab=1.5.dp; val focus=2.dp; val record=2.dp }
    object Size { val touchMin=48.dp; val iconSmall=20.dp; val filterMenuCheck=16.dp; val icon=24.dp; val iconLarge=32.dp; val checkboxGlyph=24.dp; val buttonMin=48.dp; val buttonPrimaryMin=52.dp; val fieldMin=64.dp; val pickerMin=64.dp; val topBarMin=64.dp; val brandMin=36.dp; val bottomNavMin=80.dp; val listRowMin=64.dp; val photoThumb=64.dp; val photoGridMin=88.dp; val editorActionReserve=56.dp; val sheetMaxWidth=560.dp; val dialogMaxWidth=560.dp; val menuMinWidth=196.dp; val menuMaxWidth=320.dp; val formMaxWidth=640.dp; val contentMaxWidth=840.dp; val narrowThreshold=360.dp; val mediumThreshold=600.dp; val expandedThreshold=840.dp; val compactHeightThreshold=480.dp; val pairMinCellWidth=148.dp }
    object Layout { val pageInsetCompact=16.dp; val pageInsetMedium=24.dp; val pageInsetExpanded=32.dp; val bodyGap=12.dp; val sectionGap=24.dp; val buttonGap=8.dp; val fieldActionGap=8.dp; val cardPadding=16.dp; val barPadding=16.dp; val pairGap=8.dp; const val fontScaleStackThreshold=1.3f; const val badgeFontScaleStackThreshold=1.5f; val bodyMinimumVisibleHeight=160.dp }
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

/** Fixed-ink treatment for deliberately white filter controls in either appearance. */
object V16ServiceFilterSelectorContract {
    val surface = Color.White
    val primaryInk = Color(0xFF182A30)
    val secondaryInk = Color(0xFF566870)
    val accentInk = Color(0xFF075C62)
    val selectedContainer = Color(0xFFE7F3F1)
    /** Keep the white control recognizably V16 Service without tinting its surface. */
    val outline = Color(0xFF67B9B8)
    val menuCheckSize = V16ServiceUiTokens.Size.filterMenuCheck
}

internal fun v16ServiceFilterSelectorOutline(colors: V16ServiceColorRoles): Color =
    if (colors.surface == V16ServiceUiTokens.LightColors.surface) colors.recordBorder else colors.selectionOutline

fun v16ServiceBrandNeutral(colors: V16ServiceColorRoles): Color =
    if (colors === V16ServiceUiTokens.LightColors) Color.Black else Color.White

object V16ServiceButtonContract {
    val radius = V16ServiceUiTokens.Radius.field
    val horizontalPadding = V16ServiceUiTokens.Space.xl
    val verticalPadding = V16ServiceUiTokens.Space.md
    val primaryMinHeight = V16ServiceUiTokens.Size.buttonPrimaryMin
    val secondaryMinHeight = V16ServiceUiTokens.Size.buttonMin
    val focusWidth = V16ServiceUiTokens.Stroke.focus
    val focusGap = V16ServiceUiTokens.Space.hair
    val textStyle = V16ServiceUiTokens.Type.button
    fun primaryContainer(colors:V16ServiceColorRoles,enabled:Boolean,pressed:Boolean)=if(!enabled)colors.disabledContainer else if(pressed)colors.actionPressed else colors.action
    fun primaryInk(colors:V16ServiceColorRoles,enabled:Boolean)=if(enabled)colors.onAction else colors.disabledText
    fun secondaryContainer(colors:V16ServiceColorRoles,enabled:Boolean)=if(enabled)colors.tonalCommandContainer else colors.disabledContainer
    fun secondaryInk(colors:V16ServiceColorRoles,enabled:Boolean)=if(enabled)colors.tonalCommandInk else colors.disabledText
}

enum class V16ServiceVisualState { Neutral, Info, Warning, Error, Success, Working, History }

data class V16ServiceStateStyle(val label: String, val container: Color, val ink: Color, val state: V16ServiceVisualState)

fun v16ServiceStateStyle(code: String, colors: V16ServiceColorRoles): V16ServiceStateStyle {
    val normalized = code.trim().uppercase()
    val (label, family) = when (normalized) {
        "DRAFT" -> "Draft" to V16ServiceVisualState.Warning
        "DISPATCHED" -> "Dispatched" to V16ServiceVisualState.Info
        "CONCLUDED" -> "Concluded" to V16ServiceVisualState.Neutral
        "BOOKED" -> "Booked" to V16ServiceVisualState.Info
        "WORKING" -> "Working" to V16ServiceVisualState.Working
        "OPEN" -> "Open" to V16ServiceVisualState.Info
        "CLOSED" -> "Closed" to V16ServiceVisualState.Neutral
        "ACTIVE" -> "Active" to V16ServiceVisualState.Success
        "PAUSED" -> "Paused" to V16ServiceVisualState.Warning
        "ENDED" -> "Ended" to V16ServiceVisualState.History
        "CANCELED" -> "Canceled" to V16ServiceVisualState.History
        "COMPLETED" -> "Completed" to V16ServiceVisualState.Success
        "ARCHIVED" -> "Archived" to V16ServiceVisualState.History
        "RETIRED" -> "Retired" to V16ServiceVisualState.History
        "SAVED" -> "Saved" to V16ServiceVisualState.Success
        "FAILED" -> "Not saved — action needed" to V16ServiceVisualState.Error
        "ISSUE_FOUND" -> "Issue found" to V16ServiceVisualState.Warning
        "NOT_APPLICABLE" -> "Not applicable" to V16ServiceVisualState.Neutral
        "VALUE" -> "Value recorded" to V16ServiceVisualState.Neutral
        "REVIEWED" -> "Reviewed" to V16ServiceVisualState.Success
        "READ_ONLY" -> "Read-only" to V16ServiceVisualState.Neutral
        "DISABLED" -> "Disabled" to V16ServiceVisualState.Neutral
        else -> "State unavailable" to V16ServiceVisualState.Error
    }
    val pair = when (family) {
        V16ServiceVisualState.Neutral -> colors.neutralContainer to colors.neutralInk
        V16ServiceVisualState.Info -> colors.infoContainer to colors.infoInk
        V16ServiceVisualState.Warning -> colors.warningContainer to colors.warningInk
        V16ServiceVisualState.Error -> colors.errorContainer to colors.errorInk
        V16ServiceVisualState.Success -> colors.successContainer to colors.successInk
        V16ServiceVisualState.Working -> colors.workingContainer to colors.workingInk
        V16ServiceVisualState.History -> colors.historyContainer to colors.historyInk
    }
    return V16ServiceStateStyle(label, pair.first, pair.second, family)
}
