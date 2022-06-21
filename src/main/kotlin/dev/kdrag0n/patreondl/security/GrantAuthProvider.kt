package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.Grant
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.codec.binary.Base64
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.time.Instant

private const val CHALLENGE_KEY = "GrantAuthChallenge"

fun Application.installGrantAuthProvider() {
    val config: Config by inject()

    authentication {
        grants("grantLinks") {
            grantKey = hex(config.web.grantKey)
        }
    }
}

@Serializable
data class GrantInfo(
    val grantId: Int,
)

private class GrantAuthenticationProvider(
    config: Configuration
) : AuthenticationProvider(config) {
    val encrypter = AuthenticatedEncrypter(config.grantKey)

    private suspend fun validateGrant(path: String, grantData: String?): Pair<Grant?, AuthenticationFailedCause?> {
        if (grantData == null) {
            return null to AuthenticationFailedCause.NoCredentials
        }

        // Get reference info
        val info = try {
            val encData = Base64.decodeBase64(grantData)
            val json = encrypter.decrypt(encData).decodeToString()
            Json.decodeFromString<GrantInfo>(json)
        } catch (e: Exception) {
            return null to AuthenticationFailedCause.InvalidCredentials
        }

        // Get actual grant from database
        val finalGrant = newSuspendedTransaction {
            val grant = newSuspendedTransaction {
                Grant.findById(info.grantId)
            } ?: return@newSuspendedTransaction null

            if (grant.disabled || grant.path != path || Instant.now() > grant.expireTime) {
                return@newSuspendedTransaction null
            }

            grant.accessCount++
            grant.lastAccessTime = Instant.now()

            return@newSuspendedTransaction grant
        }

        return if (finalGrant == null) {
            null to AuthenticationFailedCause.InvalidCredentials
        } else {
            finalGrant to null
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val (grant, failedCause) = validateGrant(
            call.request.path(),
            call.request.queryParameters["grant"],
        )

        if (grant == null) {
            context.challenge(CHALLENGE_KEY, failedCause!!) { challenge, call ->
                call.respond(UnauthorizedResponse())
                challenge.complete()
            }
        } else {
            context.principal(grant)
        }
    }

    class Configuration(name: String) : Config(name) {
        var grantKey = ByteArray(0)
    }
}

private fun AuthenticationConfig.grants(
    name: String,
    configure: GrantAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = GrantAuthenticationProvider(GrantAuthenticationProvider.Configuration(name).apply(configure))
    register(provider)
}
