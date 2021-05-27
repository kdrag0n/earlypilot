package dev.kdrag0n.patreondl.external.telegram

import dev.kdrag0n.patreondl.external.email.Mailer

class TelegramInviteMailer(
    private val mailer: Mailer,
    private val messageTemplate: String,
    private val benefitIndexUrl: String,
    private val fromName: String,
) {
    suspend fun sendTelegramInvite(
        address: String,
        firstName: String,
        fullName: String,
        telegramInvite: String,
    ) {
        val messageText = messageTemplate
            .replace("[FIRST_NAME]", firstName)
            .replace("[BENEFIT_INDEX_URL]", benefitIndexUrl)
            .replace("[TELEGRAM_INVITE]", telegramInvite)
            .replace("[FROM_NAME]", fromName)

        mailer.sendEmail(
            address,
            fullName,
            "Welcome, $firstName!",
            messageText,
        )
    }
}