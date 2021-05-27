package dev.kdrag0n.patreondl.external.patreon

import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.external.telegram.TelegramBot
import dev.kdrag0n.patreondl.http.PatreonApi
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun Application.webhooksModule(patreonApi: PatreonApi) {
    val jsonParser = Json {
        ignoreUnknownKeys = true
    }

    val emailEnabled = environment.config.propertyOrNull("email.enabled")?.getString()?.toBoolean() ?: false
    val mailer = if (emailEnabled) {
        Mailer(
            apiKey = environment.config.property("email.apiKey").getString(),
            fromAddress = environment.config.property("email.fromAddress").getString(),
            fromName = environment.config.property("patreon.creatorName").getString(),
        )
    } else {
        null
    }

    val telegramEnabled = environment.config.propertyOrNull("telegram.enabled")?.getString()?.toBoolean() ?: false
    val telegramBot = if (telegramEnabled) {
        TelegramBot(
            token = environment.config.property("telegram.botToken").getString(),
            groupId = environment.config.property("telegram.groupId").getString().toLong(),
        )
    } else {
        null
    }

    val benefitIndexUrl = environment.config.property("web.benefitIndexUrl").getString()

    routing {
        val webhookKey = environment.config.propertyOrNull("web.webhookKey")?.getString()
        if (webhookKey != null) {
            post("/_webhooks/patreon/${webhookKey}") {
                try {
                    val json = call.receiveText()
                    val event = jsonParser.decodeFromString<MemberPledgeEvent>(json)
                    val userId = event.data.relationships.user.data.id

                    environment.log.info("Invalidating cache for user $userId")
                    patreonApi.invalidateUser(userId)

                    val user = event.included
                        .find { it is PatreonUser && it.attributes.isEmailVerified } as PatreonUser

                    // Send Telegram invite
                    mailer?.sendWelcomeTelegramInvite(
                        user.attributes.email,
                        user.attributes.firstName,
                        benefitIndexUrl,
                        telegramBot?.generateInvite() ?: "",
                    )

                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
            }
        }
    }
}