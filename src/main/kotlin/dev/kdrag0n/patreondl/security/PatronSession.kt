package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.PatreonApi
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.sessions.*
import io.ktor.util.*
import java.io.IOException
import java.security.SecureRandom

data class PatronSession(
    val patreonUserId: String,
    val accessToken: String,
) : Principal {
    suspend fun authorize(
        patreonApi: PatreonApi,
        creatorId: String,
        minTierAmount: Int
    ): AuthorizationResult {
        val user = try {
            patreonApi.getIdentity(accessToken)
            // Kotlin doesn't support multi-catch
        } catch (e: IOException) {
            return AuthorizationResult.API_ERROR
        } catch (e: IllegalStateException) {
            return AuthorizationResult.API_ERROR
        }

        // Each step is separate in order to return a more precise error
        val creatorPledges = user.pledges.filter { it.creator.id == creatorId }
        val amountPledges = creatorPledges.filter { it.reward.amountCents >= minTierAmount }
        val validPledge = creatorPledges.find { it.declinedSince == null }

        return when {
            user.id == creatorId -> AuthorizationResult.SUCCESS // Always allow creator

            creatorPledges.isEmpty() -> AuthorizationResult.NO_PLEDGE
            amountPledges.isEmpty() -> AuthorizationResult.LOW_TIER
            validPledge == null -> AuthorizationResult.PAYMENT_DECLINED

            else -> AuthorizationResult.SUCCESS
        }
    }
}

fun Application.installPatronSessions() {
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
}