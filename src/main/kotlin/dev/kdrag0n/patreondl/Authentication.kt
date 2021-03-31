package dev.kdrag0n.patreondl

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*

private const val PATREON_OAUTH_AUTHORIZE = "https://www.patreon.com/oauth2/authorize"
private const val PATREON_OAUTH_ACCESS_TOKEN = "https://www.patreon.com/api/oauth2/token"

@KtorExperimentalLocationsAPI
@Location("/login")
private class Login

data class PatronSession(
    val patreonUserId: String,
    val accessToken: String,
) : Principal

@KtorExperimentalLocationsAPI
fun Application.authModule() {
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
    }
}

private suspend fun ApplicationCall.loginError(error: String) {
    application.environment.log.warn("Login failed: $error")
    respondText("Patreon login failed: $error", status = HttpStatusCode.BadRequest)
}