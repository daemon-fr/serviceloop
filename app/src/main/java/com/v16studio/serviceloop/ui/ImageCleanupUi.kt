package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.ImageCleanupService
import com.v16studio.serviceloop.data.ImageRetention
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ImageCleanupScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val database = remember { (context.applicationContext as ServiceLoopApplication).container.database }
    val cleanup = remember { ImageCleanupService(context, database) }
    var choice by remember { mutableStateOf(cleanup.preference()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.padding(padding).testTag("image-cleanup"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Image cleanup", style = MaterialTheme.typography.headlineSmall); Text("Keep full-resolution photos for ${choice.title.lowercase()}."); Text("ServiceLoop keeps a report-quality copy for finalized work. Working photos are never automatically removed.") }
        ImageRetention.entries.forEach { option -> item { ServiceLoopSelectionOption(choice == option, { choice = option }, option.title, Modifier.testTag("image-retention-${option.name.lowercase()}")) } }
        item {
            ServiceLoopPrimaryButton("Save image cleanup setting", { scope.launch {
                busy = true; message = null
                runCatching { withContext(Dispatchers.IO) { cleanup.savePreference(choice) } }
                    .onSuccess { message = "Image cleanup setting saved." }
                    .onFailure { if (it is CancellationException) throw it else message = it.message ?: "Setting could not be saved" }
                busy = false
            } }, Modifier.fillMaxWidth().testTag("save-image-cleanup"), enabled = !busy, busy = busy)
            ServiceLoopPrimaryButton("Run cleanup now", { scope.launch {
                busy = true; message = null
                runCatching { withContext(Dispatchers.IO) { cleanup.savePreference(choice); cleanup.runNow() } }
                    .onSuccess { result -> message = "Checked ${result.inspected} finalized photos; kept ${result.derivativesCreated} report-quality copies; removed ${result.originalsRemoved} eligible originals. ${result.retainedBecauseOfError} originals stayed because a copy could not be verified." }
                    .onFailure { if (it is CancellationException) throw it else message = it.message ?: "Cleanup could not complete" }
                busy = false
            } }, Modifier.fillMaxWidth().testTag("run-image-cleanup"), enabled = !busy && choice != ImageRetention.NEVER, busy = busy)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
