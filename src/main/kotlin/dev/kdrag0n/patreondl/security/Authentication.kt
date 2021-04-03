package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.PatreonApi
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*

private const val PATREON_OAUTH_AUTHORIZE = "https://www.patreon.com/oauth2/authorize"
private const val PATREON_OAUTH_ACCESS_TOKEN = "https://www.patreon.com/api/oauth2/token"

@KtorExperimentalLocationsAPI
@Location("/login")
private class Login

@KtorExperimentalLocationsAPI
fun Application.authModule() {
    // Session for storing OAuth access tokens
    installPatronSessions()

    val patreonProvider = OAuthServerSettings.OAuth2ServerSettings(
        name = "patreon",
        authorizeUrl = PATREON_OAUTH_AUTHORIZE,
        accessTokenUrl = PATREON_OAUTH_ACCESS_TOKEN,
        clientId = environment.config.property("patreon.clientId").getString(),
        clientSecret = environment.config.property("patreon.clientSecret").getString(),
        requestMethod = HttpMethod.Post,
    )

    val httpClient = HttpClient(Apache)
    val patreonApi = PatreonApi()

    authentication {
        // This just retrieves OAuth access tokens, session keeps track of it
        oauth("patreonOAuth") {
            client = httpClient
            providerLookup = { patreonProvider }
            urlProvider = {
                val scheme = if (environment.config.property("web.httpsOnly").getString().toBoolean()) {
                    "https"
                } else {
                    "http"
                }
                val host = request.headers["Host"]
                "$scheme://$host/login"
            }
        }

        // Used for subsequent requests
        session<PatronSession>("patronSession") {
            val creatorName = environment.config.property("patreon.creatorName").getString()
            val creatorId = environment.config.property("patreon.creatorId").getString()
            val minTierAmount = environment.config.property("patreon.minTierAmount").getString().toInt()

            challenge {
                call.respondAuthorizationResult(creatorName, creatorId, minTierAmount)
            }

            validate { session ->
                val result = session.authorize(patreonApi, creatorId, minTierAmount)
                attributes.put(AuthorizationResult.KEY, result)
                return@validate if (result == AuthorizationResult.SUCCESS) session else null
            }
        }
    }

    routing {
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

                    val user = patreonApi.getIdentity(principal.accessToken)
                    call.sessions.set(PatronSession(user.id, principal.accessToken))
                    call.respondRedirect("../")
                }
            }
        }
    }
}

private suspend fun ApplicationCall.loginError(error: String) {
    application.environment.log.warn("Login failed: $error")
    respondText("Patreon login failed: $error", status = HttpStatusCode.BadRequest)
}