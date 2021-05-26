package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.http.PatreonApi
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.sessions.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.IOException
import java.security.SecureRandom
import java.time.Instant

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
            // 401 Unauthorized = token expired
            return if (e.message?.contains("HTTP response code: 401 for") == true) {
                AuthorizationResult.TOKEN_EXPIRED
            } else {
                AuthorizationResult.API_ERROR
            }
        } catch (e: IllegalStateException) {
            // null user = unauthorized
            return AuthorizationResult.TOKEN_EXPIRED
        }

        // Each step is separate in order to return a more precise error
        val creatorPledges = user.pledges.filter { it.creator.id == creatorId }
        val amountPledges = creatorPledges.filter { it.reward.amountCents >= minTierAmount }
        val validPledge = creatorPledges.find { it.declinedSince == null }
        // Database
        val isBlocked = newSuspendedTransaction {
            val dbUser = User.findById(patreonUserId)
                ?: return@newSuspendedTransaction false
            dbUser.blocked
        }

        return when {
            // Allow blocking creator
            isBlocked -> AuthorizationResult.BLOCKED

            // Otherwise, allow the creator
            user.id == creatorId -> AuthorizationResult.SUCCESS

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