package dev.kdrag0n.patreondl.external.telegram

import dev.inmo.tgbotapi.bot.Ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.createChatInviteLink
import dev.inmo.tgbotapi.types.ChatId

class TelegramBot(
    token: String,
    groupId: Long,
) {
    private val bot = telegramBot(token)
    private val chatId = ChatId(groupId)

    suspend fun generateInvite(): String {
        val invite = bot.createChatInviteLink(
            chatId,
            membersLimit = 1,
        )

        return invite.inviteLink
    }
}