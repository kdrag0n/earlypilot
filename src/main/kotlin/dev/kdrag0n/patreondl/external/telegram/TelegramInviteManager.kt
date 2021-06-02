package dev.kdrag0n.patreondl.external.telegram

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.external.patreon.PatreonUser
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

class TelegramInviteManager(
    private val mailer: Mailer,
    private val config: Config,
    private val telegramBot: TelegramBot,
) {
    suspend fun startBot() {
        GlobalScope.launch {
            for (userId in telegramBot.removeUsers) {
                removeTelegramUser(userId)
            }
        }

        telegramBot.start()
    }

    suspend fun sendTelegramInvite(
        user: PatreonUser,
        email: String,
    ) {
        val inviteText = newSuspendedTransaction {
            val dbUser = User.findById(user.id) ?: User.new(user.id) { }
            // Skip users with existing invites
            if (dbUser.telegramInvite != null) {
                return@newSuspendedTransaction null
            }

            logger.info("Generating Telegram invite link for Patreon user ${user.id}")
            val invite = try {
                telegramBot.generateInvite()
            } catch (e: Exception) {
                logger.error("Failed to generate Telegram invite link for Patreon user ${user.id}", e)
                null
            }

            dbUser.apply {
                name = user.attributes.fullName
                this.email = email
                creationTime = user.attributes.createdAt

                telegramInvite = invite
            }

            return@newSuspendedTransaction invite
                ?: "<failed to create Telegram invite link; contact ${config.external.patreon.creatorName} for help>"
        } ?: return

        val messageText = config.external.email.messageTemplates.telegramWelcome
            .replace("[FIRST_NAME]", user.attributes.firstName)
            .replace("[BENEFIT_INDEX_URL]", config.content.benefitIndexUrl)
            .replace("[TELEGRAM_INVITE]", inviteText)
            .replace("[FROM_NAME]", config.external.patreon.creatorName)

        // Send email
        mailer.sendEmail(
            email,
            user.attributes.fullName,
            "Welcome, ${user.attributes.firstName}!",
            messageText,
        )
    }

    suspend fun removeTelegramUser(patreonUserId: String) {
        logger.info("Removing Patreon user $patreonUserId from Telegram")

        val user = newSuspendedTransaction {
            User.findById(patreonUserId)
        } ?: return logger.warn("Patreon user $patreonUserId canceled, but not found in database")

        val invite = user.telegramInvite
            ?: return logger.warn("Patreon user ${user.id} canceled with unknown Telegram invite")
        telegramBot.revokeInvite(invite)

        val telegramId = user.telegramId ?: return
        telegramBot.removeUser(telegramId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramInviteManager::class.java)
    }
}
