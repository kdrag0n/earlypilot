package dev.kdrag0n.patreondl.external.telegram

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.external.email.Mailer

class TelegramInviteManager(
    private val mailer: Mailer,
    private val config: Config,
    private val telegramBot: TelegramBot,
) {
    suspend fun sendTelegramInvite(
        address: String,
        firstName: String,
        fullName: String,
    ) {
        val invite = telegramBot.generateInvite()

        val messageText = config.external.email.messageTemplates.telegramWelcome
            .replace("[FIRST_NAME]", firstName)
            .replace("[BENEFIT_INDEX_URL]", config.content.benefitIndexUrl)
            .replace("[TELEGRAM_INVITE]", invite)
            .replace("[FROM_NAME]", config.external.patreon.creatorName)

        mailer.sendEmail(
            address,
            fullName,
            "Welcome, $firstName!",
            messageText,
        )
    }
}