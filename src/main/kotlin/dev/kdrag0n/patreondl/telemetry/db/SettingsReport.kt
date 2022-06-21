package dev.kdrag0n.patreondl.telemetry.db

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object SettingsReports : IntIdTable("tm_settings_reports") {
    val androidId = text("android_id").index()
    val versionCode = integer("version_code")
    val reportedAt = timestamp("reported_at").defaultExpression(CurrentTimestamp())
    val settings = jsonb("settings", JsonObject.serializer())
}

class SettingsReport(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SettingsReport>(SettingsReports)

    var androidId by SettingsReports.androidId
    var versionCode by SettingsReports.versionCode
    var reportedAt by SettingsReports.reportedAt
    var settings by SettingsReports.settings
}
