package dev.kdrag0n.patreondl.content

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
import java.io.File
import java.io.FileNotFoundException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant

@KtorExperimentalAPI
fun Application.exclusiveModule(dbAvailable: Boolean) {
    routing {
        // Patron-only content (eligibility already verified)
        authenticate("patronSession") {
            exclusiveGetRoute(environment, dbAvailable)

            val staticSrc = environment.config.propertyOrNull("web.staticSrc")?.getString()
            if (staticSrc != null) {
                static {
                    filesWithIndex(staticSrc, "index.html")
                }
            }

        }

        // Grants
        exclusiveGetRoute(environment, dbAvailable, "-grants", acceptGrants = true)
    }
}

private fun Route.exclusiveGetRoute(
    environment: ApplicationEnvironment,
    dbAvailable: Boolean,
    suffix: String = "",
    acceptGrants: Boolean = false,
) {
    val exclusiveSrc = environment.config.property("web.exclusiveSrc").getString()
    val contentFilter = Class.forName(environment.config.property("web.contentFilter").getString())
        .getDeclaredConstructor()
        .newInstance() as ContentFilter

    // Grants
    val creatorId = environment.config.property("patreon.creatorId").getString()
    val rawGrantKey = environment.config.propertyOrNull("web.grantKey")?.getString()
    // Lazy in case rawGrantKey is null
    val encrypter by lazy(mode = LazyThreadSafetyMode.NONE) {
        AuthenticatedEncrypter(hex(rawGrantKey!!))
    }

    get("/exclusive$suffix/{path}") {
        val path = call.parameters["path"]!!

        // Grants
        if (rawGrantKey != null) {
            // Mode for creator to generate grants for non-patrons
            val grantTag = call.request.queryParameters["grant_tag"]
            val session = call.sessions.get<PatronSession>()
            if (grantTag != null && session != null && session.patreonUserId == creatorId) {
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
        }

        // Normal file serving path
        serveExclusiveFile(environment, contentFilter, exclusiveSrc, path, dbAvailable)
    }
}

private suspend fun PipelineContext<*, ApplicationCall>.serveExclusiveFile(
    environment: ApplicationEnvironment,
    contentFilter: ContentFilter,
    exclusiveSrc: String,
    path: String,
    dbAvailable: Boolean,
) {
    val startTime = Instant.now()
    val digest = MessageDigest.getInstance(DownloadEvent.HASH_ALGORITHM)

    withContext(Dispatchers.IO) {
        // False-positive caused by IOException
        @Suppress("BlockingMethodInNonBlockingContext")
        try {
            val file = File("$exclusiveSrc/$path")
            val len = contentFilter.getFinalLength(environment, call, file.length())

            file.inputStream().use { fis ->
                call.respondOutputStreamWithLength(len, ContentType.defaultForFilePath(path)) {
                    // Hash the output data
                    val digestOs = DigestOutputStream(this, digest)
                    contentFilter.writeData(environment, call, fis, digestOs)
                }
            }
        } catch (e: FileNotFoundException) {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    // Collect the hash and log a download event
    if (dbAvailable) {
        newSuspendedTransaction(Dispatchers.IO) {
            DownloadEvent.new {
                val accessInfo = getAccessInfo(environment, call)
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

fun getAccessInfo(environment: ApplicationEnvironment, call: ApplicationCall): Pair<AccessType, String> {
    val session = call.sessions.get<PatronSession>()

    val creatorId = environment.config.property("patreon.creatorId").getString()
    return if (session != null) {
        if (session.patreonUserId == creatorId && call.request.queryParameters["id"] != null) {
            AccessType.CREATOR to call.request.queryParameters["id"]!!
        } else {
            AccessType.USER to session.patreonUserId
        }
    } else {
        val grant = call.attributes.getOrNull(ExclusiveGrant.KEY)
            ?: // Should never get here
            error("Attempting to serve file without valid grant or session")

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