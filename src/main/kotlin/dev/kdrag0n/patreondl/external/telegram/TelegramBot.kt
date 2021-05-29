package dev.kdrag0n.patreondl.external.telegram

import dev.inmo.tgbotapi.bot.Ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.createChatInviteLink
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.revokeChatInviteLink
import dev.inmo.tgbotapi.extensions.api.chat.members.kickChatMember
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviour
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onChatMemberUpdated
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.utils.PreviewFeature
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.data.Users
import kotlinx.coroutines.GlobalScope
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

class TelegramBot(
    config: Config,
) {
    private val bot = telegramBot(config.external.telegram.botToken)
    private val chatId = ChatId(config.external.telegram.groupId)

    @OptIn(PreviewFeature::class)
    suspend fun start() {
        logger.info("Starting bot with long polling")

        bot.buildBehaviour(GlobalScope) {
            onChatMemberUpdated { event ->
                val invite = event.inviteLink?.inviteLink
                    ?: return@onChatMemberUpdated

                newSuspendedTransaction {
                    val user = User.find { Users.telegramInvite eq invite }.firstOrNull()
                        ?: return@newSuspendedTransaction logger.warn("User ${event.user.id} joined group with unknown invite $invite")

                    logger.info("Associating Patreon user ${user.id} with Telegram user ${event.user.id.chatId}")
                    user.telegramId = event.user.id.chatId
                }
            }
        }.start()
    }

    suspend fun generateInvite(): String {
        val invite = bot.createChatInviteLink(
            chatId,
            membersLimit = 1,
        )

        return invite.inviteLink
    }

    suspend fun removeUser(id: Long) {
        bot.kickChatMember(chatId, UserId(id))
    }

    suspend fun revokeInvite(invite: String) {
        bot.revokeChatInviteLink(chatId, invite)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramBot::class.java)
    }
}