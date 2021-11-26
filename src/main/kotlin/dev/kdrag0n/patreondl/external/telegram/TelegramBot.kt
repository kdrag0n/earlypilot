package dev.kdrag0n.patreondl.external.telegram

import dev.inmo.tgbotapi.bot.Ktor.telegramBot
import dev.inmo.tgbotapi.bot.exceptions.CommonRequestException
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.createChatInviteLinkWithLimitedMembers
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.revokeChatInviteLink
import dev.inmo.tgbotapi.extensions.api.chat.members.banChatMember
import dev.inmo.tgbotapi.extensions.api.chat.members.unbanChatMember
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onChatMemberUpdated
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.utils.requireFromUserMessage
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.utils.PreviewFeature
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.data.Users
import dev.kdrag0n.patreondl.splitWhitespace
import kotlinx.coroutines.channels.Channel
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

@OptIn(PreviewFeature::class)
class TelegramBot(
    config: Config,
) {
    private val bot = telegramBot(config.external.telegram.botToken)
    private val chatId = ChatId(config.external.telegram.groupId)
    private val ownerId = ChatId(config.external.telegram.ownerId)

    val removeUsers = Channel<String>()
    val declinedUsers = Channel<Pair<Int, String>>()

    suspend fun start() {
        logger.info("Starting bot with long polling")

        bot.buildBehaviourWithLongPolling {
            // Link Patreon and Telegram users together
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
                    val user = User.find { Users.telegramInvite like invite.replace("...", "%") }
                        .limit(1)
                        .firstOrNull()
                        ?: return@newSuspendedTransaction logger.warn("User ${event.user.id} joined group with unknown invite $invite")

                    logger.info("Associating Patreon user ${user.id} with Telegram user ${event.user.id.chatId}")
                    user.telegramId = event.user.id.chatId
                }
            }

            /*
             * Management commands
             */

            // Help
            onOwnerCommand("help") { ctx ->
                reply(ctx, COMMANDS_HELP)
            }

            // Remove a list of Patreon user IDs
            onOwnerListCommand("removeusers", "Removing", "Removed") { user ->
                removeUsers.send(user)
            }

            // Send declined payment reminders to a list of Patreon user IDs
            onOwnerListCommand("declinedusers1", "Reminding", "Reminded") { user ->
                declinedUsers.send(1 to user)
            }
            onOwnerListCommand("declinedusers2", "Reminding", "Reminded") { user ->
                declinedUsers.send(2 to user)
            }
            onOwnerListCommand("declinedusers3", "Reminding", "Reminded") { user ->
                declinedUsers.send(3 to user)
            }
            onOwnerListCommand("declinedusers4", "Reminding", "Reminded") { user ->
                declinedUsers.send(4 to user)
            }
        }.start()
    }

    suspend fun generateInvite(name: String? = null): String {
        val invite = bot.createChatInviteLinkWithLimitedMembers(
            chatId,
            membersLimit = 1,
            name = name,
        )

        return invite.inviteLink
    }

    suspend fun removeUser(id: Long) {
        try {
            val user = UserId(id)
            bot.banChatMember(chatId, user)
            bot.unbanChatMember(chatId, user, onlyIfBanned = true)
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
                bot.sendMessage(ownerId, "Revoke invite: $invite", disableWebPagePreview = true)
            } else if (e.response.errorCode == 400 && e.response.description == "Bad Request: INVITE_HASH_EXPIRED") {
                /* Ignore attempts to revoke expired invites */
            } else {
                throw e
            }
        }
    }

    // Users not present in the database need manual dunning reminders
    suspend fun requestManualDunning(reminderId: Int, userId: String) {
        bot.sendMessage(ownerId, "Send dunning reminder $reminderId: Patreon user $userId")
    }

    private suspend inline fun BehaviourContext.onOwnerCommand(
        name: String,
        crossinline block: suspend BehaviourContext.(CommonMessage<TextContent>) -> Unit,
    ) {
        onCommand(name, requireOnlyCommandInMessage = false) { ctx ->
            // Owner only
            if (ctx.requireFromUserMessage().user.id != ownerId) {
                return@onCommand
            }

            block(ctx)
        }
    }

    private suspend inline fun BehaviourContext.onOwnerListCommand(
        name: String,
        actionPresent: String,
        actionPast: String,
        crossinline userCallback: suspend (String) -> Unit,
    ) {
        onOwnerCommand(name) { ctx ->
            val userIds = ctx.content.text.splitWhitespace().let { it.subList(1, it.size) }

            val statusMsg = reply(ctx, "$actionPresent ${userIds.size} Patreon users...")
            userIds.forEach { userId ->
                userCallback(userId)
            }

            bot.editMessageText(statusMsg, "$actionPast ${userIds.size} Patreon users.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramBot::class.java)
        private val COMMANDS_HELP = """
            Commands that accept whitespace-separated Patreon user IDs:
            /removeusers - Remove users
            /declinedusers1 - Remind declined users (#1; +0 days; 7 days left)
            /declinedusers2 - Remind declined users (#2; +2 days; 5 days left)
            /declinedusers3 - Remind declined users (#3; +3 days; 2 days left)
            /declinedusers4 - Remind declined users (#4; +2 days; 0 days left)
        """.trimIndent()
    }
}
