package com.clipflow.app.player

/**
 * Centralized repository and version control endpoints for EZTube self-updating.
 * Change repository paths and remote version control URLs here in one place.
 */
object AppUpdateConfig {
    const val RELEASES_API_URL = "https://api.github.com/repos/ezzatemam1982-hue/EZTube/releases/latest"
    const val VERSION_CONTROL_URL = "https://raw.githubusercontent.com/ezzatemam1982-hue/EZTube/main/version_control.json"
}
