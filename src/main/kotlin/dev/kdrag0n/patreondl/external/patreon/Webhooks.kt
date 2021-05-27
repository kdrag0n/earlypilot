package dev.kdrag0n.patreondl.external.patreon

import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.external.telegram.TelegramInviteMailer
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

    val fromName = environment.config.property("patreon.creatorName").getString()
    val mailer = if (environment.config.property("email.enabled").getString().toBoolean()) {
        Mailer(
            apiKey = environment.config.property("email.apiKey").getString(),
            fromAddress = environment.config.property("email.fromAddress").getString(),
            fromName = fromName,
        )
    } else {
        null
    }

    val telegramBot = if (environment.config.property("telegram.enabled").getString().toBoolean()) {
        TelegramBot(
            token = environment.config.property("telegram.botToken").getString(),
            groupId = environment.config.property("telegram.groupId").getString().toLong(),
        )
    } else {
        null
    }

    val inviteMailer =  if (mailer != null && telegramBot != null) {
        TelegramInviteMailer(
            mailer = mailer,
            messageTemplate = environment.config.property("email.messageTemplates.telegramWelcome").getString(),
            benefitIndexUrl = environment.config.property("web.benefitIndexUrl").getString(),
            fromName = fromName,
        )
    } else {
        null
    }

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
                    inviteMailer?.sendTelegramInvite(
                        user.attributes.email,
                        user.attributes.firstName,
                        user.attributes.fullName,
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