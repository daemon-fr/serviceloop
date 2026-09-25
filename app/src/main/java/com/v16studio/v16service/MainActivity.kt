package com.v16studio.v16service

import android.os.Bundle
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.v16studio.v16service.reminders.ReminderCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import com.v16studio.v16service.data.V16_SERVICE_SYNC_MIME

class MainActivity : ComponentActivity() {
    private var notificationRoute by mutableStateOf<String?>(null)
    private var incomingV16ServiceSync by mutableStateOf<String?>(null)
    private var incomingV16ServiceSyncEvent by mutableStateOf(0)
    private val incomingFileIntentMutex = Mutex()
    private val viewModel: V16ServiceViewModel by viewModels {
        V16ServiceViewModel.Factory((application as V16ServiceApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptReminderIntent(intent)
        acceptWorkPackageIntent(intent)
        setContent {
            V16ServiceTheme(appearancePreferences = (application as V16ServiceApplication).container.appearancePreferences, window = window) {
                V16ServiceApp(viewModel, notificationRoute, incomingV16ServiceSync, incomingV16ServiceSyncEvent, (application as V16ServiceApplication).container.appearancePreferences)
            }
        }
    }

    public override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptReminderIntent(intent); acceptWorkPackageIntent(intent) }

    override fun onResume() { super.onResume(); viewModel.onAppResumed() }

    private fun acceptReminderIntent(intent: Intent?) {
        val route = intent?.getStringExtra(ReminderCoordinator.EXTRA_ROUTE) ?: return
        if (route != "home" && !route.matches(Regex("visit/[A-Za-z0-9._-]+"))) return
        val expectedDataset = intent.getStringExtra(ReminderCoordinator.EXTRA_DATASET)
        val app = application as V16ServiceApplication
        app.container.applicationScope.launch {
            val current = app.container.database.v16ServiceDao().recoveryMetadata()?.datasetId
            if (expectedDataset == null || expectedDataset == current) runOnUiThread { notificationRoute = route }
        }
    }

    private fun acceptWorkPackageIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data ?: intent.clipData?.getItemAt(0)?.uri
            Intent.ACTION_SEND -> if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: return
        val declaredMime = intent?.type
        lifecycleScope.launch {
            incomingFileIntentMutex.withLock {
                val recognized = withContext(Dispatchers.IO) {
                    val resolvedMime = runCatching { contentResolver.getType(uri) }.getOrNull()
                    val displayName = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                        queryOpenableDisplayName(contentResolver, uri)
                    } else {
                        uri.lastPathSegment
                    }
                    isV16ServiceFileIdentity(uri, declaredMime, resolvedMime, displayName)
                }
                if (recognized) {
                    incomingV16ServiceSync = uri.toString()
                    incomingV16ServiceSyncEvent++
                }
            }
        }
    }
}

/** A content URI's path can be an opaque document ID, so query its advertised name. */
internal fun queryOpenableDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn < 0 || !cursor.moveToFirst()) null else cursor.getString(nameColumn)
    }
}.getOrNull()

internal fun isV16ServiceFileIdentity(
    uri: Uri,
    declaredMime: String?,
    resolvedMime: String?,
    displayName: String?,
): Boolean {
    fun isNativeMime(value: String?) =
        value?.substringBefore(';')?.trim()?.equals(V16_SERVICE_SYNC_MIME, ignoreCase = true) == true

    if (isNativeMime(declaredMime) || isNativeMime(resolvedMime)) return true
    val filename = if (uri.scheme == ContentResolver.SCHEME_CONTENT) displayName else uri.lastPathSegment
    return filename?.endsWith(".v16service", ignoreCase = true) == true
}
