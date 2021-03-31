package dev.kdrag0n.patreondl

import dev.kdrag0n.patreondl.filters.ContentFilter
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.auth.*
import io.ktor.util.*
import io.ktor.locations.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.sessions.*
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

fun main(args: Array<String>) {
    EngineMain.main(args)
}

const val PATREON_OAUTH_AUTHORIZE = "https://www.patreon.com/oauth2/authorize"
const val PATREON_OAUTH_ACCESS_TOKEN = "https://www.patreon.com/api/oauth2/token"

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(production: Boolean = false) {
    // Typed routes
    install(Locations)

    // In case users download with a parallel downloader, e.g. aria2
    install(PartialContent) {
        maxRangeCount = 10
    }

    // Session for storing OAuth access tokens
    install(Sessions) {
        cookie<PatronSession>("patronSession") {
            cookie.extensions["SameSite"] = "Strict"
            cookie.secure = production
        }
    }

    // Support HEAD for completeness
    install(AutoHeadResponse)

    // Support CORS (e.g. for web installer downloads)
    install(CORS) {
        // Send cookies for session authentication
        allowCredentials = true

        val schemes = if (production) listOf("https") else listOf("https", "http")
        for (host in environment.config.property("web.corsAllowed").getList()) {
            host(host, schemes = schemes)
        }
    }

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { genericStatusError(it) }
        status(HttpStatusCode.Unauthorized) { genericStatusError(it) }
        status(HttpStatusCode.BadRequest) { genericStatusError(it) }
    }

    val patreonProvider = OAuthServerSettings.OAuth2ServerSettings(
        name = "patreon",
        authorizeUrl = PATREON_OAUTH_AUTHORIZE,
        accessTokenUrl = PATREON_OAUTH_ACCESS_TOKEN,
        clientId = environment.config.property("patreon.clientId").getString(),
        clientSecret = environment.config.property("patreon.clientSecret").getString(),
        requestMethod = HttpMethod.Post,
    )

    val httpClient = HttpClient(Apache)

    authentication {
        // This just retrieves OAuth access tokens, session keeps track of it
        oauth("patreonOAuth") {
            client = httpClient
            providerLookup = { patreonProvider }
            urlProvider = { url(Login()) }
        }

        // Used for subsequent requests
        session<PatronSession>("patronSession") {
            challenge("/login")

            val creatorId = environment.config.property("patreon.creatorId").getString()
            val minTierAmount = environment.config.property("patreon.minTierAmount").getString().toInt()

            validate { session ->
                val user = PatreonApi.getIdentity(session.accessToken)
                val validPledge = user.pledges.find { pledge ->
                    pledge.creator.id == creatorId &&
                            pledge.reward.amountCents >= minTierAmount &&
                            pledge.declinedSince == null
                }

                return@validate if (validPledge != null) session else null
            }
        }
    }

    routing {
        // Redirect root to index of benefits on separate static site
        val benefitIndexUrl = environment.config.propertyOrNull("web.benefitIndexUrl")
        if (benefitIndexUrl != null) {
            get("/") {
                call.respondRedirect(benefitIndexUrl.getString())
            }
        }

        // Wrap /login endpoint with OAuth provider for automatic redirects
        authenticate("patreonOAuth") {
            location<Login> {
                param("error") {
                    handle {
                        call.loginError(call.parameters["error"] ?: "")
                    }
                }

                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse>()
                    if (principal !is OAuthAccessTokenResponse.OAuth2) {
                        call.loginError("Invalid OAuth access token response")
                        return@handle
                    }

                    val user = PatreonApi.getIdentity(principal.accessToken)
                    call.sessions.set(PatronSession(user.id, principal.accessToken))
                    call.respondRedirect("../")
                }
            }
        }

        // Patron-only content (eligibility already verified)
        authenticate("patronSession") {
            val exclusiveSrc = environment.config.property("web.exclusiveSrc").getString()
            val contentFilter = Class.forName(environment.config.property("web.contentFilter").getString())
                .getDeclaredConstructor()
                .newInstance() as ContentFilter

            // False-positive caused by IOException
            @Suppress("BlockingMethodInNonBlockingContext")
            get("/exclusive/{name}") {
                val path = call.parameters["name"]
                    ?: return@get call.respondText("Missing", status = HttpStatusCode.BadRequest)

                withContext(Dispatchers.IO) {
                    FileInputStream("$exclusiveSrc/$path").use { fis ->
                        call.respondOutputStream(
                            contentType = ContentType.defaultForFilePath(path)
                        ) {
                            contentFilter.writeData(fis, this, call)
                        }
                    }
                }
            }

            static("exclusive") {
                files("exclusive_src")
            }
        }
    }
}

suspend fun ApplicationCall.loginError(error: String) {
    application.environment.log.warn("Login failed: $error")
    respondText("Patreon login failed: $error", status = HttpStatusCode.BadRequest)
}

suspend fun PipelineContext<*, ApplicationCall>.genericStatusError(status: HttpStatusCode) {
    call.respondText("${status.value} ${status.description}")
}

@KtorExperimentalLocationsAPI
@Location("/login")
class Login

data class PatronSession(
    val patreonUserId: String,
    val accessToken: String,
) : Principal