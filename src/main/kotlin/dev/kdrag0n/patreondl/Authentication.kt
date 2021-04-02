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
import io.ktor.util.*
import java.io.IOException
import java.security.SecureRandom

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
    val patreonApi = PatreonApi()

    // Session for storing OAuth access tokens
    install(Sessions) {
        cookie<PatronSession>("patronSession") {
            cookie.extensions["SameSite"] = "Strict"
            cookie.secure = environment.config.property("web.httpsOnly").getString().toBoolean()

            val encKey = environment.config.propertyOrNull("web.sessionEncryptKey")
            if (encKey == null) {
                environment.log.warn("No session encryption key; cookie will not be encrypted or authenticated")
            } else {
                val authKey = environment.config.property("web.sessionAuthKey").getString()
                val ivGen = SecureRandom.getInstanceStrong()

                transform(SessionTransportTransformerEncrypt(hex(encKey.getString()), hex(authKey), {
                    // Workaround for Ktor bug: IV length is determined by key length
                    ByteArray(16).apply {
                        ivGen.nextBytes(this)
                    }
                }))
            }
        }
    }

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
            challenge("/login")

            val creatorId = environment.config.property("patreon.creatorId").getString()
            val minTierAmount = environment.config.property("patreon.minTierAmount").getString().toInt()

            validate { session ->
                val user = try {
                    patreonApi.getIdentity(session.accessToken)
                } catch (e: Exception) {
                    // Workaround for lack of multi-catch support
                    when (e) {
                        is IOException, is IllegalStateException -> {
                            // Expired session, request authentication again
                            return@validate null
                        }
                        else -> throw e
                    }
                }

                val validPledge = user.pledges.find { pledge ->
                    pledge.creator.id == creatorId &&
                            pledge.reward.amountCents >= minTierAmount &&
                            pledge.declinedSince == null
                }

                if (validPledge == null) {
                    val prettyAmount = "$" + String.format("%.02f", minTierAmount.toFloat() / 100)
                    respondText(
                        """
                            |You must pledge at least $prettyAmount per month for access to exclusive downloads.
                            |To pledge on Patreon, visit this link: https://patreon.com/user?u=$creatorId
                        """.trimMargin(),
                        status = HttpStatusCode.Forbidden
                    )
                    return@validate null
                }

                return@validate session
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