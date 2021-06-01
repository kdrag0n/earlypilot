package dev.kdrag0n.patreondl.external.telegram

import dev.inmo.tgbotapi.bot.Ktor.telegramBot
import dev.inmo.tgbotapi.bot.exceptions.CommonRequestException
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.createChatInviteLink
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.revokeChatInviteLink
import dev.inmo.tgbotapi.extensions.api.chat.members.kickChatMember
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
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
    private val ownerId = ChatId(config.external.telegram.ownerId)

    @OptIn(PreviewFeature::class)
    suspend fun start() {
        logger.info("Starting bot with long polling")

        bot.buildBehaviour(GlobalScope) {
            onChatMemberUpdated { event ->
                // Validate chat
                if (event.chat.id != chatId) {
                    return@onChatMemberUpdated
                }

                // Event includes invite link = "member joined via invite link"
                val invite = event.inviteLink?.inviteLink
                    ?: return@onChatMemberUpdated

                newSuspendedTransaction {
                    // If we didn't create the invite, Telegram truncates it with "..."
                    // Enough of the link remains for it to be identifiable, so find it with LIKE
                    val user = User.find { Users.telegramInvite like invite.replace("...", "%") }.firstOrNull()
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
        try {
            bot.kickChatMember(chatId, UserId(id))
        } catch (e: CommonRequestException) {
            // People can leave the group voluntarily, so handle this gracefully
            if (e.response.errorCode == 400 && e.response.description == "Bad Request: PARTICIPANT_ID_INVALID") {
                logger.debug("User $id not in group")
            } else {
                throw e
            }
        }
    }

    suspend fun revokeInvite(invite: String) {
        try {
            bot.revokeChatInviteLink(chatId, invite)
        } catch (e: CommonRequestException) {
            // We can't revoke other admins' invite links, so ask the owner to do it
            if (e.response.errorCode == 400 && e.response.description == "Bad Request: CHAT_ADMIN_REQUIRED") {
                logger.error("Failed to revoke $invite - created by another admin")
                bot.sendMessage(ownerId, "Revoke invite: $invite")
            } else {
                throw e
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramBot::class.java)
    }
}