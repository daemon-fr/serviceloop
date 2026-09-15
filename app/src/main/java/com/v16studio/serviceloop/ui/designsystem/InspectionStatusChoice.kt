package com.v16studio.serviceloop.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon

/** A semantic inspection answer with its restrained visual state family. */
data class InspectionStatusChoice(
    val key: String,
    val label: String,
    val icon: Int,
    val semanticColor: androidx.compose.ui.graphics.Color,
    val testTag: String? = null,
)

data class InspectionChoiceWidthAllocation(
    val firstWidth: Int,
    val secondWidth: Int,
    val vertical: Boolean,
)

/**
 * Allocates a two-choice row around the midpoint while protecting one-line and touch minima.
 * Widths are pixels so this helper can be tested independently of Compose density.
 */
fun allocateInspectionChoiceWidths(
    rowWidth: Int,
    gap: Int,
    firstRequiredWidth: Int,
    secondRequiredWidth: Int,
    comfortableFloorFraction: Float = 0.36f,
): InspectionChoiceWidthAllocation {
    val usable = (rowWidth - gap).coerceAtLeast(0)
    val firstRequired = firstRequiredWidth.coerceAtLeast(0)
    val secondRequired = secondRequiredWidth.coerceAtLeast(0)
    if (firstRequired + secondRequired > usable) {
        return InspectionChoiceWidthAllocation(0, 0, vertical = true)
    }
    val midpoint = usable / 2
    val floor = (usable * comfortableFloorFraction).toInt().coerceAtLeast(0)
    val preferredFirst = maxOf(firstRequired, floor)
    val preferredSecond = maxOf(secondRequired, floor)
    val (firstMinimum, secondMinimum) = if (preferredFirst + preferredSecond <= usable) {
        preferredFirst to preferredSecond
    } else {
        firstRequired to secondRequired
    }
    val first = midpoint.coerceIn(firstMinimum, usable - secondMinimum)
    return InspectionChoiceWidthAllocation(first, usable - first, vertical = false)
}

@Composable
fun ServiceLoopInspectionStatusGrid(
    choices: List<InspectionStatusChoice>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(choices.size == 4) { "Inspection status grid requires exactly four choices" }
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val loose = Constraints(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity)
        val natural = choices.map { choice ->
            subcompose("natural-${choice.key}") {
                InspectionStatusChoiceSurface(choice, selected = choice.key == selectedKey, enabled = enabled, onClick = {}, modifier = Modifier, interactive = false)
            }.single().measure(loose)
        }
        val gapPx = ServiceLoopUiTokens.Layout.pairGap.roundToPx()
        val width = constraints.maxWidth
        val first = allocateInspectionChoiceWidths(width, gapPx, natural[0].width, natural[1].width)
        val second = allocateInspectionChoiceWidths(width, gapPx, natural[2].width, natural[3].width)
        val measured = choices.mapIndexed { index, choice ->
            subcompose("choice-${choice.key}") {
                InspectionStatusChoiceSurface(
                    choice = choice,
                    selected = choice.key == selectedKey,
                    enabled = enabled,
                    onClick = { onSelected(choice.key) },
                    modifier = Modifier,
                )
            }.single().let { measurable ->
                val allocation = if (index < 2) first else second
                if (allocation.vertical) {
                    measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                } else {
                    val allocatedWidth = if (index % 2 == 0) allocation.firstWidth else allocation.secondWidth
                    measurable.measure(constraints.copy(minWidth = allocatedWidth, maxWidth = allocatedWidth))
                }
            }
        }
        val rowGap = gapPx
        val rowOneHeight = if (first.vertical) measured[0].height + rowGap + measured[1].height else maxOf(measured[0].height, measured[1].height)
        val rowTwoHeight = if (second.vertical) measured[2].height + rowGap + measured[3].height else maxOf(measured[2].height, measured[3].height)
        val height = rowOneHeight + rowTwoHeight + rowGap
        layout(width, height) {
            fun placeRow(startIndex: Int, y: Int, allocation: InspectionChoiceWidthAllocation) {
                if (allocation.vertical) {
                    measured[startIndex].placeRelative(0, y)
                    measured[startIndex + 1].placeRelative(0, y + measured[startIndex].height + rowGap)
                } else {
                    measured[startIndex].placeRelative(0, y)
                    measured[startIndex + 1].placeRelative(allocation.firstWidth + rowGap, y)
                }
            }
            placeRow(0, 0, first)
            placeRow(2, rowOneHeight + rowGap, second)
        }
    }
}

@Composable
private fun InspectionStatusChoiceSurface(
    choice: InspectionStatusChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    interactive: Boolean = true,
) {
    val colors = LocalServiceLoopTokens.current
    val semanticColor = choice.semanticColor
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .then(if (interactive) Modifier.selectable(enabled = enabled, selected = selected, role = Role.RadioButton, onClick = onClick) else Modifier)
            .then(if (interactive) Modifier.semantics { this.selected = selected } else Modifier)
            .then(if (!interactive || choice.testTag == null) Modifier else Modifier.testTag(choice.testTag)),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
        color = if (selected) semanticColor.copy(alpha = 0.10f) else colors.surface,
        border = BorderStroke(
            ServiceLoopUiTokens.Stroke.outline,
            if (selected) semanticColor.copy(alpha = 0.75f) else colors.outlineControl,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Space.sm, vertical = ServiceLoopUiTokens.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
        ) {
            ServiceLoopIcon(
                choice.icon,
                contentDescription = null,
                modifier = Modifier.size(ServiceLoopUiTokens.Size.iconSmall),
                tint = if (selected) semanticColor else semanticColor.copy(alpha = 0.30f),
            )
            Text(
                choice.label,
                color = if (enabled) colors.textPrimary else colors.disabledText,
                style = ServiceLoopUiTokens.Type.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
