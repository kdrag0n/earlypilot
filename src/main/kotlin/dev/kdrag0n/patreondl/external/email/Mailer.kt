package dev.kdrag0n.patreondl.external.email

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class Mailer(
    config: Config,
) {
    private val client = HttpClient(Apache) {
        install(Auth) {
            basic {
                username = "api"
                password = config.external.email.apiKey
                sendWithoutRequest = true
            }
        }
    }
    private val creatorEmail = "${config.external.patreon.creatorName} <${config.external.email.fromAddress}>"
    private val personalEmail = "${config.external.email.personalName} <${config.external.email.personalAddress}>"

    suspend fun sendEmail(
        toAddress: String,
        toName: String?,
        subject: String,
        bodyText: String,
        personal: Boolean = false,
    ) {
        val fromEmail = if (personal) personalEmail else creatorEmail
        val toEmail = if (toName == null) toAddress else "$toName <$toAddress>"
        client.post<HttpStatement>("https://api.mailgun.net/v3/mg.kdrag0n.dev/messages") {
            parameter("from", fromEmail)
            parameter("to", toEmail)
            parameter("subject", subject)
            parameter("text", bodyText)
        }.execute()
    }

    suspend fun sendEmail(
        user: User,
        subject: String,
        bodyText: String,
        personal: Boolean = false,
    ) {
        sendEmail(
            user.email,
            user.name,
            subject,
            bodyText,
            personal = personal,
        )
    }
}

class MailgunException(message: String) : Exception(message)
