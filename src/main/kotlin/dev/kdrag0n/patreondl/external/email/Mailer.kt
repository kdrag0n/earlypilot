package dev.kdrag0n.patreondl.external.email

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class Mailer(
    config: Config,
) {
    private val client = HttpClient(Apache) {
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(
                        username = "api",
                        password = config.external.email.apiKey,
                    )
                }

                sendWithoutRequest { true }
            }
        }
    }

    private val senders = mapOf(
        EmailType.CREATOR to "${config.external.patreon.creatorName} <${config.external.email.fromAddress}>",
        EmailType.DUNNING to "${config.external.patreon.creatorName} <${config.external.email.dunningAddress}>",
        EmailType.PERSONAL to "${config.external.email.personalName} <${config.external.email.personalAddress}>",
    )

    suspend fun sendEmail(
        toAddress: String,
        toName: String?,
        subject: String,
        bodyText: String,
        type: EmailType = EmailType.CREATOR,
    ) {
        val fromEmail = senders[type]!!
        val toEmail = if (toName == null) toAddress else "$toName <$toAddress>"
        client.post("https://api.mailgun.net/v3/mg.kdrag0n.dev/messages") {
            parameter("from", fromEmail)
            parameter("to", toEmail)
            parameter("subject", subject)
            parameter("text", bodyText)
        }.discardRemaining()
    }

    suspend fun sendEmail(
        user: User,
        subject: String,
        bodyText: String,
        type: EmailType = EmailType.CREATOR,
    ) {
        sendEmail(
            user.email,
            user.name,
            subject,
            bodyText,
            type = type,
        )
    }
}

class MailgunException(message: String) : Exception(message)

enum class EmailType {
    CREATOR,
    DUNNING,
    PERSONAL
}
