package com.v16studio.serviceloop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPresetChoiceGroup
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServiceLoopPresetChoiceLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextWrapsCompactPresetsWithoutClippingOrShrinkingTargets() {
        val options = listOf(1 to "1h", 3 to "3h", 6 to "6h", 12 to "12h", 24 to "24h", 48 to "48h")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ServiceLoopTheme {
                    Box(Modifier.width(320.dp).testTag("large-text-presets")) {
                        ServiceLoopPresetChoiceGroup(options, 3, {}, testTagPrefix = "large-text-preset")
                    }
                }
            }
        }
        val nodes = options.map { compose.onNodeWithTag("large-text-preset-${it.first}").fetchSemanticsNode() }
        val heights = nodes.map { it.boundsInRoot.height }
        val tops = nodes.map { it.boundsInRoot.top }.distinct()
        assertTrue(heights.all { it >= 48f })
        assertTrue("large text should wrap instead of clipping", tops.size > 1)
        assertTrue(nodes.all { it.boundsInRoot.right <= 320f })
    }
}
