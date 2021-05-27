package dev.kdrag0n.patreondl.external.email

import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email
import dev.kdrag0n.patreondl.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Mailer(
    config: Config,
) {
    private val client = SendGrid(config.external.email.apiKey)
    private val fromEmail = Email(config.external.email.fromAddress, config.external.patreon.creatorName)

    suspend fun sendEmail(toAddress: String, toName: String?, subject: String, bodyText: String) {
        val toEmail = Email(toAddress, toName)
        val content = Content("text/plain", bodyText)

        val request = Request().apply {
            method = Method.POST
            endpoint = "mail/send"

            // IOException = JSON marshal error - not blocking
            @Suppress("BlockingMethodInNonBlockingContext")
            body = Mail(fromEmail, subject, toEmail, content).build()
        }

        withContext(Dispatchers.IO) {
            // On I/O thread
            @Suppress("BlockingMethodInNonBlockingContext")
            val resp = client.api(request)
            if (resp.statusCode !in 200..299) {
                throw SendgridException("[${resp.statusCode}] ${resp.body}")
            }
        }
    }
}

class SendgridException(message: String) : Exception(message)