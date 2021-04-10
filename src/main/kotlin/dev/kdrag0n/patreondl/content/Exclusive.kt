package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.content.filters.ContentFilter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

@KtorExperimentalAPI
fun Application.exclusiveModule() {
    routing {
        // Patron-only content (eligibility already verified)
        authenticate("patronSession") {
            exclusiveGetRoute(environment)

            val staticSrc = environment.config.propertyOrNull("web.staticSrc")?.getString()
            if (staticSrc != null) {
                static {
                    filesWithIndex(staticSrc, "index.html")
                }
            }

        }

        // Grants
        exclusiveGetRoute(environment, "-grants", acceptGrants = true)
    }
}

private fun Route.exclusiveGetRoute(
    environment: ApplicationEnvironment,
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
                val grantInfo = ExclusiveGrant(
                    path = path,
                    tag = grantTag,
                    timestamp = System.currentTimeMillis(),
                )

                // Pad to nearest 16-byte boundary to avoid side-channel attacks
                var grantJson = Json.encodeToString(grantInfo)
                grantJson += " ".repeat(grantJson.length % 16)
                // Encrypt padded JSON data
                val grantData = hex(encrypter.encrypt(grantJson.encodeToByteArray()))

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
                        Json.decodeFromString<ExclusiveGrant>(encrypter.decrypt(hex(grantData)).decodeToString())
                    } catch (e: Exception) {
                        return@get call.respond(HttpStatusCode.Unauthorized)
                    }

                    // Always return unauthorized to avoid leaking info
                    if (grantInfo.path != path || grantInfo.timestamp > System.currentTimeMillis()) {
                        return@get call.respond(HttpStatusCode.Unauthorized)
                    }

                    // Valid grant, put attribute and continue serving file
                    call.attributes.put(ExclusiveGrant.KEY, grantInfo)
                } else {
                    return@get call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }

        // Normal file serving path
        withContext(Dispatchers.IO) {
            // False-positive caused by IOException
            @Suppress("BlockingMethodInNonBlockingContext")
            try {
                val file = File("$exclusiveSrc/$path")
                val len = contentFilter.getFinalLength(environment, call, file.length())

                file.inputStream().use { fis ->
                    call.respondOutputStreamWithLength(len, ContentType.defaultForFilePath(path)) {
                        contentFilter.writeData(environment, call, fis, this)
                    }
                }
            } catch (e: FileNotFoundException) {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

@Serializable
data class ExclusiveGrant(
    val path: String,
    val tag: String,
    val timestamp: Long,
) {
    companion object {
        val KEY = AttributeKey<ExclusiveGrant>("exclusive.grant")
    }
}