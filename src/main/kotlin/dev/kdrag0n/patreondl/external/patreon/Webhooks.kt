package dev.kdrag0n.patreondl.external.patreon

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.external.telegram.TelegramInviteManager
import dev.kdrag0n.patreondl.http.PatreonApi
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Application.webhooksModule() {
    val config: Config by inject()
    val patreonApi: PatreonApi by inject()
    val inviteMailer: TelegramInviteManager by inject()

    routing {
        post("/_webhooks/patreon/${config.external.patreon.webhookKey}") {
            val event = call.receive<MemberPledgeEvent>()
            val userId = event.data.relationships.user.data.id

            environment.log.info("Invalidating cache for user $userId")
            patreonApi.invalidateUser(userId)

            val user = event.included
                .find { it is PatreonUser && it.attributes.isEmailVerified } as PatreonUser

            // Send Telegram invite
            inviteMailer.sendTelegramInvite(
                user.attributes.email,
                user.attributes.firstName,
                user.attributes.fullName,
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}