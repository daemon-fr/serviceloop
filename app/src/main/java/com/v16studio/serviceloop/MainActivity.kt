package com.v16studio.serviceloop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ServiceLoopViewModel by viewModels {
        ServiceLoopViewModel.Factory((application as ServiceLoopApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ServiceLoopTheme {
                ServiceLoopApp(viewModel)
            }
        }
    }
}
