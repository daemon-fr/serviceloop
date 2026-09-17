package com.v16studio.serviceloop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val WORK_NEW_VISIT_SLOT_KEY = "work-new-visit-slot"
internal const val WORK_NEW_VISIT_SHORT_PAGE_SPACER_KEY = "work-new-visit-short-page-spacer"
private val WorkNewVisitFloatingClearance = ServiceLoopUiTokens.Size.touchMin + ServiceLoopUiTokens.Space.lg

internal enum class WorkNewVisitActionState { UNRESOLVED, FLOATING, DOCKED }

internal data class WorkNewVisitLayout(
    val totalItemsCount: Int,
    val visibleItemsCount: Int,
    val slotFullyVisible: Boolean,
) {
    val isMeaningful: Boolean
        get() = totalItemsCount > 0 && visibleItemsCount > 0
}

internal fun resolveWorkNewVisitActionState(
    current: WorkNewVisitActionState,
    layout: WorkNewVisitLayout,
): WorkNewVisitActionState = when {
    current == WorkNewVisitActionState.UNRESOLVED && !layout.isMeaningful -> WorkNewVisitActionState.UNRESOLVED
    current == WorkNewVisitActionState.UNRESOLVED -> if (layout.slotFullyVisible) WorkNewVisitActionState.DOCKED else WorkNewVisitActionState.FLOATING
    current == WorkNewVisitActionState.FLOATING && layout.slotFullyVisible -> WorkNewVisitActionState.DOCKED
    else -> current
}

/** Resolves once from the first meaningful list layout, then only moves from FLOATING to DOCKED. */
@Composable
internal fun rememberWorkNewVisitActionState(listState: LazyListState): WorkNewVisitActionState {
    var actionState by rememberSaveable { mutableStateOf(WorkNewVisitActionState.UNRESOLVED) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val slot = layout.visibleItemsInfo.firstOrNull { it.key == WORK_NEW_VISIT_SLOT_KEY }
            WorkNewVisitLayout(
                totalItemsCount = layout.totalItemsCount,
                visibleItemsCount = layout.visibleItemsInfo.size,
                slotFullyVisible = slot != null && slot.offset >= layout.viewportStartOffset &&
                    slot.offset + slot.size <= layout.viewportEndOffset,
            )
        }.distinctUntilChanged().collect { fullyVisible ->
            actionState = resolveWorkNewVisitActionState(actionState, fullyVisible)
        }
    }
    return actionState
}

/** Fills only the unused viewport before a docked slot on a genuinely short, non-scrollable page. */
@Composable
internal fun rememberWorkNewVisitDockedBottomSpacer(
    listState: LazyListState,
    actionState: WorkNewVisitActionState,
): Dp {
    val density = LocalDensity.current
    var spacerHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState, actionState, density) {
        if (actionState != WorkNewVisitActionState.DOCKED) {
            spacerHeightPx = 0
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = listState.layoutInfo
            val slot = layout.visibleItemsInfo.firstOrNull { it.key == WORK_NEW_VISIT_SLOT_KEY }
            val currentSpacer = layout.visibleItemsInfo
                .firstOrNull { it.key == WORK_NEW_VISIT_SHORT_PAGE_SPACER_KEY }
                ?.size ?: 0
            if (listState.firstVisibleItemIndex == 0 && slot != null) {
                val slotBottomWithoutSpacer = slot.offset + slot.size - currentSpacer
                val normalBottomPadding = with(density) { ServiceLoopUiTokens.Space.sm.toPx().toInt() }
                (layout.viewportEndOffset - slotBottomWithoutSpacer - normalBottomPadding).coerceAtLeast(0)
            } else {
                0
            }
        }.distinctUntilChanged().collect { spacerHeightPx = it }
    }
    return with(density) { spacerHeightPx.toDp() }
}

@Composable
internal fun WorkNewVisitReservedSlot(
    actionState: WorkNewVisitActionState,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().testTag("new-visit-slot")) {
        val visible = actionState == WorkNewVisitActionState.DOCKED
        Box(
            Modifier.fillMaxWidth()
                .then(if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
        ) {
            ServiceLoopPrimaryButton(
                "New visit",
                onClick = if (visible) onClick else { {} },
                enabled = visible,
                modifier = Modifier.fillMaxWidth().testTag("new-visit-work-bottom"),
                leadingIcon = { ServiceLoopIcon(ServiceLoopIcons.Add, null, Modifier.size(ServiceLoopUiTokens.Size.icon)) },
            )
        }
    }
}

@Composable
internal fun WorkNewVisitFloatingAction(
    actionState: WorkNewVisitActionState,
    onClick: () -> Unit,
    bottomInset: Dp = 0.dp,
    respectNavigationBars: Boolean = true,
) {
    AnimatedVisibility(
        visible = actionState == WorkNewVisitActionState.FLOATING,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 3 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 3 },
    ) {
        Box(
            Modifier.fillMaxSize()
                .then(if (respectNavigationBars) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    start = ServiceLoopUiTokens.Space.md,
                    end = ServiceLoopUiTokens.Space.md,
                    bottom = bottomInset + ServiceLoopUiTokens.Space.md,
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.card),
                tonalElevation = ServiceLoopUiTokens.Elevation.rest,
                shadowElevation = ServiceLoopUiTokens.Elevation.rest,
            ) {
                ServiceLoopPrimaryButton(
                    "New visit",
                    onClick,
                    Modifier.widthIn(min = 144.dp).testTag("new-visit-work-floating"),
                    leadingIcon = { ServiceLoopIcon(ServiceLoopIcons.Add, null, Modifier.size(ServiceLoopUiTokens.Size.icon)) },
                )
            }
        }
    }
}

internal fun workNewVisitListBottomPadding(actionState: WorkNewVisitActionState): Dp =
    if (actionState == WorkNewVisitActionState.FLOATING) WorkNewVisitFloatingClearance else ServiceLoopUiTokens.Space.sm

internal val WorkNewVisitDueBottomInset: Dp = 0.dp
