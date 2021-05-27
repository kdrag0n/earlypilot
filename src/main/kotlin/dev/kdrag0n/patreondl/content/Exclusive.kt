package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.content.filters.ContentFilter
import dev.kdrag0n.patreondl.data.AccessType
import dev.kdrag0n.patreondl.data.DownloadEvent
import dev.kdrag0n.patreondl.security.AuthenticatedEncrypter
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.codec.binary.Base64
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
        authenticate("patronSession") {
            exclusiveGetRoute(config, environment)

            static {
                filesWithIndex(config.content.staticSrc, "index.html")
            }
        }

        // Grants
        exclusiveGetRoute(config, environment, "-grants", acceptGrants = true)
    }
}

private fun Route.exclusiveGetRoute(
    config: Config,
    environment: ApplicationEnvironment,
    suffix: String = "",
    acceptGrants: Boolean = false,
) {
    val contentFilter: ContentFilter by inject()
    val encrypter = AuthenticatedEncrypter(hex(config.web.grantKey))

    get("/exclusive$suffix/{path}") {
        val path = call.parameters["path"]!!

        // Mode for creator to generate grants for non-patrons
        val grantTag = call.request.queryParameters["grant_tag"]
        val session = call.sessions.get<PatronSession>()
        if (grantTag != null && session != null && session.patreonUserId == config.external.patreon.creatorId) {
            // Convert String -> Float -> Long to allow for sub-hour precision in query parameters
            val durationHours = (call.request.queryParameters["expires"] ?: "48").toFloat()
            val durationMs = (durationHours * 60 * 60 * 1000).toLong()
            val grantInfo = ExclusiveGrant(
                path = path,
                tag = grantTag,
                expireTime = System.currentTimeMillis() + durationMs,
            )

            // Pad to nearest 16-byte boundary to avoid side-channel attacks
            var grantJson = Json.encodeToString(grantInfo)
            grantJson += " ".repeat(grantJson.length % 16)
            // Encrypt padded JSON data
            val grantData = Base64.encodeBase64String(encrypter.encrypt(grantJson.encodeToByteArray()))

            val url = call.url {
                encodedPath = "/exclusive-grants/$path"
                parameters.clear()
                parameters["grant"] = grantData
            }
            return@get call.respondText(url)
        }

        // Mode for non-patrons to use a grant
        val grantData = call.request.queryParameters["grant"]
        if (acceptGrants) {
            if (grantData != null) {
                val grantInfo = try {
                    Json.decodeFromString<ExclusiveGrant>(encrypter.decrypt(Base64.decodeBase64(grantData)).decodeToString())
                } catch (e: Exception) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                // Always return forbidden to avoid leaking info
                if (grantInfo.path != path || System.currentTimeMillis() > grantInfo.expireTime) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                // Valid grant, put attribute and continue serving file
                call.attributes.put(ExclusiveGrant.KEY, grantInfo)
            } else {
                return@get call.respond(HttpStatusCode.Forbidden)
            }
        }

        // Normal file serving path
        serveExclusiveFile(environment, config, contentFilter, config.content.exclusiveSrc, path)
    }
}

private suspend fun PipelineContext<*, ApplicationCall>.serveExclusiveFile(
    environment: ApplicationEnvironment,
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
        val grant = call.attributes.getOrNull(ExclusiveGrant.KEY)
            // Should never get here
            ?: error("Attempting to serve file without valid grant or session")

        AccessType.GRANT to grant.tag
    }
}

@Serializable
data class ExclusiveGrant(
    val path: String,
    val tag: String,
    val expireTime: Long,
) {
    companion object {
        val KEY = AttributeKey<ExclusiveGrant>("exclusive.grant")
    }
}