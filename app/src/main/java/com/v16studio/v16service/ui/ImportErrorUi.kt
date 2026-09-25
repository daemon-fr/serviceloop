package com.v16studio.v16service.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal class ImportPurposeMismatchException(val actualPurpose: String) : IllegalArgumentException()

internal data class ImportErrorPresentation(
    val title: String,
    val explanation: String,
    val alternateAction: String? = null,
)

internal enum class ImportRouteKind { WORK_ASSIGNMENT, SHARED_DATA }

internal fun importErrorPresentation(failure: Throwable, route: ImportRouteKind): ImportErrorPresentation {
    if (failure is ImportPurposeMismatchException && route == ImportRouteKind.WORK_ASSIGNMENT) {
        val (what, action) = when (failure.actualPurpose) {
            "FULL_WORKSPACE" -> "a V16 Service workspace file" to "Open Import shared data"
            "DATA_TRANSFER" -> "a V16 Service shared-data file" to "Open Import shared data"
            "WORK_RESULT" -> "a V16 Service final-results file" to "Open Import shared data"
            else -> "a different kind of V16 Service file" to "Open Import shared data"
        }
        return ImportErrorPresentation(
            "This file is for a different import",
            "This is $what, but Import assigned work accepts work assignment packages. $action to preview this file in the matching import flow.",
            action,
        )
    }

    val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString(" ")
    if (messages.contains("unsupported", ignoreCase = true) && messages.contains("version", ignoreCase = true)) {
        return ImportErrorPresentation(
            "V16 Service file version not supported",
            "This V16 Service file uses a format version this app cannot import. Ask the sender to export it from a compatible V16 Service version, then try again.",
        )
    }

    return when (route) {
        ImportRouteKind.WORK_ASSIGNMENT -> ImportErrorPresentation(
            "Could not read this work assignment",
            "This file is unreadable or is not a valid V16 Service file. Choose a supported V16 Service work assignment file and try again.",
        )
        ImportRouteKind.SHARED_DATA -> ImportErrorPresentation(
            "Could not read this V16 Service file",
            "This file is unreadable or is not a valid V16 Service file. Choose a supported .v16service file and try again.",
        )
    }
}

@Composable
internal fun ImportErrorDialog(
    error: ImportErrorPresentation,
    onDismiss: () -> Unit,
    onAlternateAction: (() -> Unit)? = null,
) {
    AlertDialog(
        modifier = Modifier.testTag("import-error-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(error.title, Modifier.testTag("import-error-title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error.explanation, Modifier.testTag("import-error-explanation"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("import-error-close")) { Text("Close") }
        },
        confirmButton = {
            val action = error.alternateAction
            if (action != null && onAlternateAction != null) {
                TextButton(onClick = onAlternateAction, modifier = Modifier.testTag("import-error-alternate-action")) { Text(action) }
            }
        },
    )
}
