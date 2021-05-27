package dev.kdrag0n.patreondl.external.telegram

import dev.inmo.tgbotapi.bot.Ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.createChatInviteLink
import dev.inmo.tgbotapi.types.ChatId
import dev.kdrag0n.patreondl.config.Config

class TelegramBot(
    config: Config,
) {
    private val bot = telegramBot(config.external.telegram.botToken)
    private val chatId = ChatId(config.external.telegram.groupId)

    suspend fun generateInvite(): String {
        val invite = bot.createChatInviteLink(
            chatId,
            membersLimit = 1,
        )

        return invite.inviteLink
    }
}