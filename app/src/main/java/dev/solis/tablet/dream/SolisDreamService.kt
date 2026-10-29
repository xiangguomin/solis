package dev.solis.tablet.dream

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import dev.solis.tablet.ui.dashboard.SolisDashboardScreen
import dev.solis.tablet.ui.theme.SolisTheme

/**
 * Android Screen Saver (Daydream) service so the Lenovo tablet automatically
 * launches Solis when docked or charging.
 */
class SolisDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        val composeView = ComposeView(this).apply {
            setContent {
                SolisTheme {
                    SolisDashboardScreen()
                }
            }
        }
        setContentView(composeView)
    }
}
