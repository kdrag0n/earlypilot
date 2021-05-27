package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.content.filters.ContentFilter
import dev.kdrag0n.patreondl.data.AccessType
import dev.kdrag0n.patreondl.data.DownloadEvent
import dev.kdrag0n.patreondl.security.AuthenticatedEncrypter
import dev.kdrag0n.patreondl.security.GrantInfo
import dev.kdrag0n.patreondl.security.PatronSession
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.io.File
import java.io.FileNotFoundException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant

@KtorExperimentalAPI
fun Application.exclusiveModule() {
    val config: Config by inject()

    routing {
        // Patron-only content (eligibility already verified)
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
    val encrypter = AuthenticatedEncrypter(hex(config.web.grantKey))

    get("/exclusive/{path}") {
        val path = call.parameters["path"]!!

        // Mode for creator to generate grants for non-patrons
        val grantTag = call.request.queryParameters["grant_tag"]
        val session = call.sessions.get<PatronSession>()
        if (grantTag != null && session != null && session.patreonUserId == config.external.patreon.creatorId) {
            // Convert from Float to allow for sub-hour precision in query parameters
            val durationHours = (call.request.queryParameters["expires"] ?: "48").toFloat()
            val grant = GrantInfo.generateUrl(call, encrypter, grantTag, durationHours)
            return@get call.respondText(grant)
        }

        // Normal file serving path
        serveExclusiveFile(config, contentFilter, config.content.exclusiveSrc, path)
    }
}

private suspend fun PipelineContext<*, ApplicationCall>.serveExclusiveFile(
    config: Config,
    contentFilter: ContentFilter,
    exclusiveSrc: String,
    path: String,
) {
    val startTime = Instant.now()
    val digest = MessageDigest.getInstance(DownloadEvent.HASH_ALGORITHM)

    val success = withContext(Dispatchers.IO) {
        // False-positive caused by IOException
        @Suppress("BlockingMethodInNonBlockingContext")
        try {
            val file = File("$exclusiveSrc/$path")
            val len = contentFilter.getFinalLength(call, file.length())

            file.inputStream().use { fis ->
                call.respondOutputStreamWithLength(len, ContentType.defaultForFilePath(path)) {
                    // Hash the output data
                    val digestOs = DigestOutputStream(this, digest)
                    contentFilter.writeData(call, fis, digestOs)
                }
            }

            true
        } catch (e: FileNotFoundException) {
            call.respond(HttpStatusCode.NotFound)
            false
        }
    }

    // Collect the hash and log a download event
    if (success) {
        newSuspendedTransaction(Dispatchers.IO) {
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

fun getAccessInfo(config: Config, call: ApplicationCall): Pair<AccessType, String> {
    val session = call.sessions.get<PatronSession>()

    return if (session != null) {
        if (session.patreonUserId == config.external.patreon.creatorId && call.request.queryParameters["id"] != null) {
            AccessType.CREATOR to call.request.queryParameters["id"]!!
        } else {
            AccessType.USER to session.patreonUserId
        }
    } else {
        val grant = call.principal<GrantInfo>()
            // Should never get here
            ?: error("Attempting to serve file without valid grant or session")

        AccessType.GRANT to grant.tag
    }
}