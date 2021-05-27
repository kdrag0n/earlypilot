package dev.kdrag0n.patreondl.external.patreon

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.http.PatreonApi
import dev.kdrag0n.patreondl.security.AuthorizationResult
import dev.kdrag0n.patreondl.security.PatronSession
import dev.kdrag0n.patreondl.security.respondAuthorizationResult
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.time.Instant

private const val PATREON_OAUTH_AUTHORIZE = "https://www.patreon.com/oauth2/authorize"
private const val PATREON_OAUTH_ACCESS_TOKEN = "https://www.patreon.com/api/oauth2/token"

@KtorExperimentalLocationsAPI
@Location("/login")
private class Login

@KtorExperimentalLocationsAPI
fun Application.installPatreonAuthProvider() {
    val config: Config by inject()
    val patreonApi: PatreonApi by inject()
    val httpClient: HttpClient by inject()

    val patreonProvider = OAuthServerSettings.OAuth2ServerSettings(
        name = "patreon",
        authorizeUrl = PATREON_OAUTH_AUTHORIZE,
        accessTokenUrl = PATREON_OAUTH_ACCESS_TOKEN,
        clientId = config.external.patreon.clientId,
        clientSecret = config.external.patreon.clientSecret,
        requestMethod = HttpMethod.Post,
    )

    authentication {
        // This just retrieves OAuth access tokens, session keeps track of it
        oauth("patreonOAuth") {
            client = httpClient
            providerLookup = { patreonProvider }
            urlProvider = { url(Login()) }
        }

        // Used for subsequent requests
        session<PatronSession>("patronSession") {
            challenge {
                call.respondAuthorizationResult(
                    config.external.patreon.creatorName,
                    config.external.patreon.creatorId,
                    config.external.patreon.minTierAmount,
                )
            }

            validate { session ->
                val result = session.authorize(
                    patreonApi,
                    config.external.patreon.creatorId,
                    config.external.patreon.minTierAmount,
                )
                attributes.put(AuthorizationResult.KEY, result)

                // Update info in database
                newSuspendedTransaction {
                    val dbUser = User.findById(session.patreonUserId)
                        ?: return@newSuspendedTransaction false

                    dbUser.authState = result
                    dbUser.activityIp = request.origin.remoteHost
                    dbUser.activityTime = Instant.now()
                }

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

                    // Save user info in database
                    newSuspendedTransaction {
                        val dbUser = User.findById(user.id) ?: User.new(user.id) { }
                        dbUser.apply {
                            name = user.fullName
                            email = user.email
                            accessToken = principal.accessToken
                            creationTime = user.created.toInstant()
                            loginTime = Instant.now()
                            loginIp = call.request.origin.remoteHost
                        }
                    }

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