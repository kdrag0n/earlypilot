package dev.kdrag0n.patreondl.telemetry

import dev.kdrag0n.patreondl.telemetry.db.SettingsReport
import dev.kdrag0n.patreondl.telemetry.db.SettingsReports
import dev.kdrag0n.patreondl.telemetry.model.SettingsReportRequest
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

fun Application.telemetryModule() {
    transaction {
        SchemaUtils.create(SettingsReports)
    }

    routing {
        post("/api/v1/telemetry/report_settings") {
            val report = call.receive<SettingsReportRequest>()

            // Anti-abuse
            if (report.settings.keys.size > 50) {
                call.respond(HttpStatusCode.BadRequest)
            }

            newSuspendedTransaction {
                // Only keep the latest report for each SSAID
                val entry = SettingsReport.find { SettingsReports.androidId eq report.ssaid }
                    .limit(1).firstOrNull() ?: SettingsReport.new { }
                entry.apply {
                    androidId = report.ssaid
                    versionCode = report.versionCode
                    reportedAt = Instant.now()
                    settings = report.settings
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}
