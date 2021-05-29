package dev.kdrag0n.patreondl.external.patreon

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.external.telegram.TelegramInviteManager
import dev.kdrag0n.patreondl.http.PatreonApi
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

fun Application.webhooksModule() {
    val config: Config by inject()
    val patreonApi: PatreonApi by inject()
    val inviteManager: TelegramInviteManager by inject()

    GlobalScope.launch {
        inviteManager.startBot()
    }

    routing {
        post("/_webhooks/patreon/${config.external.patreon.webhookKey}/{event_type}") {
            val event = call.receive<MemberPledgeEvent>()
            val eventType = call.parameters["event_type"]!!
            val userId = event.data.relationships.user.data.id

            environment.log.info("Invalidating cache for user $userId")
            patreonApi.invalidateUser(userId)

            val user = event.included
                .find { it is PatreonUser && it.attributes.isEmailVerified } as PatreonUser

            when (eventType) {
                // New users
                "members:pledge:create", "members:pledge:update" -> {
                    // Send Telegram invite
                    inviteManager.sendTelegramInvite(user)
                }

                // Canceled users
                "members:pledge:delete" -> {
                    // TODO: only remove users after access expiration
                    inviteManager.removeTelegramUser(user)
                }

                // Invalid events
                else -> environment.log.warn("Patreon webhook: unknown event type $eventType")
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}