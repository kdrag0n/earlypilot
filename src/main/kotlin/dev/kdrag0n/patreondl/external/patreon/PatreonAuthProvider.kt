package dev.kdrag0n.patreondl.external.patreon

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.security.AuthenticatedEncrypter
import dev.kdrag0n.patreondl.security.AuthorizationResult
import dev.kdrag0n.patreondl.security.PatronSession
import dev.kdrag0n.patreondl.security.respondAuthorizationResult
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.codec.binary.Base64
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.time.Instant

private const val PATREON_OAUTH_AUTHORIZE = "https://www.patreon.com/oauth2/authorize"
private const val PATREON_OAUTH_ACCESS_TOKEN = "https://www.patreon.com/api/oauth2/token"

private const val OAUTH_NONCE_EXPIRE_TIME = 15 * 60 * 1000 // 15 minutes

@KtorExperimentalLocationsAPI
@Location("/login")
private class Login

@Serializable
private data class OauthState(
    val time: Long,
    val url: String,
)

private fun decodeOauthState(encrypter: AuthenticatedEncrypter, state: String): OauthState {
    val data = encrypter.decrypt(Base64.decodeBase64(state))
    return Json.decodeFromString(data.decodeToString())
}

private class UrlNonceManager(
    private val encrypter: AuthenticatedEncrypter,
    private val url: String,
) : NonceManager {
    override suspend fun newNonce(): String {
        val state = OauthState(
            time = System.currentTimeMillis(),
            url = url,
        )

        val data = Json.encodeToString(state).encodeToByteArray()
        return Base64.encodeBase64URLSafeString(encrypter.encrypt(data))
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        // Check whether decryption & authentication succeeds
        return try {
            val state = decodeOauthState(encrypter, nonce)

            // Validate time
            val expireTime = state.time + OAUTH_NONCE_EXPIRE_TIME
            System.currentTimeMillis() <= expireTime
        } catch (e: Exception) {
            false
        }
    }
}

@KtorExperimentalLocationsAPI
fun Application.installPatreonAuthProvider() {
    val config: Config by inject()
    val patreonApi: PatreonApi by inject()
    val httpClient: HttpClient by inject()

    val encrypter = AuthenticatedEncrypter(hex(config.web.sessionEncryptKey))

    authentication {
        // This just retrieves OAuth access tokens, session keeps track of it
        oauth("patreonOAuth") {
            client = httpClient
            urlProvider = { url(Login()) }

            providerLookup = {
                // Can't reuse the provider because we need to create a new NonceManager for each request URL
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "patreon",
                    authorizeUrl = PATREON_OAUTH_AUTHORIZE,
                    accessTokenUrl = PATREON_OAUTH_ACCESS_TOKEN,
                    clientId = config.external.patreon.clientId,
                    clientSecret = config.external.patreon.clientSecret,
                    requestMethod = HttpMethod.Post,
                    nonceManager = UrlNonceManager(encrypter, request.uri),
                )
            }
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

                    // Decode state and redirect to original destination
                    val state = decodeOauthState(encrypter, call.parameters["state"]!!)
                    // Handle /login?next_url=... redirect
                    val url = Url(state.url)
                    if (url.encodedPath == "login" && "next_url" in url.parameters) {
                        val nextUrl = url.parameters["next_url"]!!

                        // Must be relative to our host
                        if ("://" !in nextUrl) {
                            return@handle call.respondRedirect(nextUrl, permanent = false)
                        }
                    }

                    // Fallback: redirect to index
                    call.respondRedirect(config.content.benefitIndexUrl, permanent = false)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.loginError(error: String) {
    application.environment.log.warn("Login failed: $error")
    respondText("Patreon login failed: $error", status = HttpStatusCode.BadRequest)
}
