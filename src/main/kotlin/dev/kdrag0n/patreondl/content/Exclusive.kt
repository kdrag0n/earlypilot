package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.content.filters.ContentFilter
import dev.kdrag0n.patreondl.data.AccessType
import dev.kdrag0n.patreondl.data.DownloadEvent
import dev.kdrag0n.patreondl.data.Grant
import dev.kdrag0n.patreondl.external.telegram.TelegramInviteManager
import dev.kdrag0n.patreondl.respondErrorPage
import dev.kdrag0n.patreondl.security.GrantManager
import dev.kdrag0n.patreondl.security.PatronSession
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant

@OptIn(KtorExperimentalAPI::class)
fun Application.exclusiveModule() {
    val config: Config by inject()
    val telegramManager: TelegramInviteManager by inject()

    routing {
        // Patron-only content (tier already verified)
        authenticate("patronSession") {
            get("/telegram/join") {
                val session = call.sessions.get<PatronSession>()!!
                val invite = telegramManager.getUserInvite(session.patreonUserId)

                if (invite == null) {
                    call.respondErrorPage(
                        HttpStatusCode.OK,
                        "Telegram invite not available",
                        "Please contact ${config.external.patreon.creatorName} for an invite to the Telegram group. Sorry for the inconvenience!",
                    )
                } else {
                    // Redirect, but don't cache
                    call.response.headers.append(HttpHeaders.CacheControl, "no-store, max-age=0")
                    call.respondRedirect(invite, permanent = false)
                }
            }
        }

        // Patreon- and grant-only content
        authenticate("patronSession", "grantLinks") {
            exclusiveGetRoute(config)

            static {
                filesWithIndex(config.content.staticSrc, "index.html")
            }
        }
    }
}

private fun Route.exclusiveGetRoute(
    config: Config,
) {
    val contentFilter: ContentFilter by inject()
    val grantManager: GrantManager by inject()

    get("/exclusive/{path}") {
        val path = call.parameters["path"]!!

        // Mode for creator to generate grants for non-patrons
        val grantTag = call.request.queryParameters["grant_tag"]
        val session = call.sessions.get<PatronSession>()
        if (grantTag != null && session != null && session.patreonUserId == config.external.patreon.creatorId) {
            // Convert from Float to allow for sub-hour precision in query parameters
            val durationHours = (call.request.queryParameters["expires"] ?: "48").toFloat()
            val grantUrl = grantManager.createGrantUrl(
                call.request.path(),
                grantTag,
                Grant.Type.CREATOR,
                durationHours,
            )
            return@get call.respondText(grantUrl)
        }

        // Normal file serving path
        serveExclusiveFile(config, contentFilter, path)
    }
}

private suspend fun PipelineContext<*, ApplicationCall>.serveExclusiveFile(
    config: Config,
    contentFilter: ContentFilter,
    path: String,
) {
    val startTime = Instant.now()
    val digest = MessageDigest.getInstance(DownloadEvent.HASH_ALGORITHM)

    withContext(Dispatchers.IO) {
        val file = File("${config.content.exclusiveSrc}/$path")
        if (!file.exists()) {
            return@withContext call.respond(HttpStatusCode.NotFound)
        }

        try {
            val len = contentFilter.getFinalLength(call, file.length())

            file.inputStream().use { fis ->
                call.respondOutputStreamWithLength(len, ContentType.defaultForFilePath(path)) {
                    // Hash the output data
                    val digestOs = DigestOutputStream(this, digest)
                    contentFilter.writeData(call, fis, digestOs)
                }
            }
        } finally {
            // Collect the hash and log a download event
            // Failed/partial downloads should still be logged to keep track of possible accesses
            transaction {
                DownloadEvent.new {
                    val accessInfo = getAccessInfo(config, call)
                    accessType = accessInfo.first
                    tag = accessInfo.second

                    fileName = path
                    fileHash = hex(digest.digest())
                    downloadTime = startTime
                    clientIp = call.request.origin.remoteHost
                }
            }
        }
    }
}

fun getAccessInfo(config: Config, call: ApplicationCall): Pair<AccessType, String> {
    val session = call.sessions.get<PatronSession>()

    return if (session != null) {
        if (session.patreonUserId == config.external.patreon.creatorId && call.request.queryParameters["id"] != null) {
            AccessType.CREATOR to call.request.queryParameters["id"]!!
        } else {
            AccessType.USER to session.patreonUserId
        }
    } else {
        val grant = call.principal<Grant>()
            // Should never get here
            ?: error("Attempting to serve file without valid grant or session")

        when (grant.type) {
            Grant.Type.CREATOR -> AccessType.GRANT
            Grant.Type.PURCHASE -> AccessType.PURCHASE
        } to grant.tag
    }
}
