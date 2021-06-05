package dev.kdrag0n.patreondl.external.email

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.external.email.EmailTemplates.Companion.execute

class DunningMailer(
    private val config: Config,
    private val mailer: Mailer,
    private val emailTemplates: EmailTemplates,
) {
    suspend fun sendReminder(reminderId: Int, user: User) {
        val (subject, template) = when (reminderId) {
            1 -> "Your access will end soon..." to emailTemplates.declinedReminder1
            2 -> "Second Notice: Please update your billing information." to emailTemplates.declinedReminder2
            3 -> "Your Patreon access will end in 2 days." to emailTemplates.declinedReminder3
            4 -> "Patreon Cancellation: Your Patreon access has ended." to emailTemplates.declinedReminder4
            else -> error("Invalid reminder ID")
        }

        val messageText = template.execute(mapOf(
            "config" to config,
            "user" to user,
        ))

        mailer.sendEmail(user, subject, messageText, personal = reminderId >= 3)
    }
}
