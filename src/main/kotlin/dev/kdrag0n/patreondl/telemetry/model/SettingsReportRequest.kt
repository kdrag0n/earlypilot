package dev.kdrag0n.patreondl.telemetry.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SettingsReportRequest(
    // Anonymous install ID
    val ssaid: String,
    // App info
    val versionCode: Int,
    // Settings
    val settings: JsonObject,
)
