package com.v16studio.serviceloop

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.v16studio.serviceloop.reminders.ReminderCoordinator
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme

class MainActivity : ComponentActivity() {
    private var notificationRoute by mutableStateOf<String?>(null)
    private val viewModel: ServiceLoopViewModel by viewModels {
        ServiceLoopViewModel.Factory((application as ServiceLoopApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptReminderIntent(intent)
        setContent {
            ServiceLoopTheme {
                ServiceLoopApp(viewModel, notificationRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptReminderIntent(intent) }

    override fun onResume() { super.onResume(); viewModel.onAppResumed() }

    private fun acceptReminderIntent(intent: Intent?) {
        val route = intent?.getStringExtra(ReminderCoordinator.EXTRA_ROUTE) ?: return
        if (route != "home" && !route.matches(Regex("visit/[A-Za-z0-9._-]+"))) return
        val expectedDataset = intent.getStringExtra(ReminderCoordinator.EXTRA_DATASET)
        val app = application as ServiceLoopApplication
        app.container.applicationScope.launch {
            val current = app.container.database.serviceLoopDao().recoveryMetadata()?.datasetId
            if (expectedDataset == null || expectedDataset == current) runOnUiThread { notificationRoute = route }
        }
    }
}
