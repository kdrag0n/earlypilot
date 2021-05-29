package dev.kdrag0n.patreondl.external.telegram

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.external.patreon.PatreonUser
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

class TelegramInviteManager(
    private val mailer: Mailer,
    private val config: Config,
    private val telegramBot: TelegramBot,
) {
    suspend fun startBot() {
        telegramBot.start()
    }

    suspend fun sendTelegramInvite(
        user: PatreonUser,
    ) {
        val invite = newSuspendedTransaction {
            val dbUser = User.findById(user.id) ?: User.new(user.id) { }
            // Skip users with existing invites
            if (dbUser.telegramInvite != null) {
                return@newSuspendedTransaction null
            }

            logger.info("Generating Telegram invite link for Patreon user ${user.id}")
            val invite = telegramBot.generateInvite()
            dbUser.apply {
                name = user.attributes.fullName
                email = user.attributes.email
                creationTime = user.attributes.createdAt

                telegramInvite = invite
            }

            return@newSuspendedTransaction invite
        } ?: return

        val messageText = config.external.email.messageTemplates.telegramWelcome
            .replace("[FIRST_NAME]", user.attributes.firstName)
            .replace("[BENEFIT_INDEX_URL]", config.content.benefitIndexUrl)
            .replace("[TELEGRAM_INVITE]", invite)
            .replace("[FROM_NAME]", config.external.patreon.creatorName)

        // Send email
        mailer.sendEmail(
            user.attributes.email,
            user.attributes.fullName,
            "Welcome, ${user.attributes.firstName}!",
            messageText,
        )
    }

    suspend fun removeTelegramUser(patreonUser: PatreonUser) {
        logger.info("Removing Patreon user ${patreonUser.id} from Telegram")

        val user = newSuspendedTransaction {
            User.findById(patreonUser.id)
        } ?: return logger.warn("Patreon user ${patreonUser.id} canceled, but not found in database")

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