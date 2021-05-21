package dev.kdrag0n.patreondl.events

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

    routing {
        val webhookKey = environment.config.propertyOrNull("web.webhookKey")?.getString()

        if (webhookKey != null) {
            post("/_webhooks/patreon/${webhookKey}") {
                val json = call.receiveText()
                val event = jsonParser.decodeFromString<MemberPledgeEvent>(json)
                val userId = event.data.relationships.user.data.id

                environment.log.info("Invalidating cache for user $userId")
                patreonApi.invalidateUser(userId)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}