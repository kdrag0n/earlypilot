package dev.kdrag0n.patreondl.external.telegram

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.User
import dev.kdrag0n.patreondl.external.email.DunningMailer
import dev.kdrag0n.patreondl.external.email.EmailTemplates
import dev.kdrag0n.patreondl.external.email.EmailTemplates.Companion.execute
import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.external.patreon.PatreonUser
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

class TelegramInviteManager(
    private val config: Config,
    private val mailer: Mailer,
    private val telegramBot: TelegramBot,
    private val dunningMailer: DunningMailer,
    private val emailTemplates: EmailTemplates,
) {
    suspend fun startBot() {
        // Remove users
        GlobalScope.launch {
            for (userId in telegramBot.removeUsers) {
                removeTelegramUser(userId)
            }
        }

        // Remind users about declined payments
        GlobalScope.launch {
            for ((reminderId, userId) in telegramBot.declinedUsers) {
                val user = newSuspendedTransaction {
                    User.findById(userId)
                }

                if (user != null) {
                    dunningMailer.sendReminder(reminderId, user)
                } else {
                    telegramBot.requestManualDunning(reminderId, userId)
                }
            }
        }

        telegramBot.start()
    }

    suspend fun sendTelegramInvite(
        user: PatreonUser,
        email: String,
    ) {
        val dbUser = newSuspendedTransaction {
            val dbUser = User.findById(user.id)
                ?: User.new(user.id) { }

            // Skip users with existing invites
            if (dbUser.telegramInvite != null) {
                return@newSuspendedTransaction null
            }

            logger.info("Generating Telegram invite link for Patreon user ${user.id}")
            val invite = try {
                telegramBot.generateInvite("Patreon user ${user.id}")
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
        } ?: return

        val messageText = emailTemplates.telegramWelcome.execute(mapOf(
            "user" to user,
            "config" to config,
        ))

        // Send email
        mailer.sendEmail(
            dbUser,
            "Welcome, ${dbUser.firstName}!",
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

        newSuspendedTransaction {
            // Clear invite so the user gets a new invite when re-pledging
            user.telegramInvite = null
        }
    }

    suspend fun getUserInvite(patreonUserId: String) = newSuspendedTransaction {
        val user = User.findById(patreonUserId)
            ?: error("Attempt to get invite for unknown user $patreonUserId")

        user.telegramInvite
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramInviteManager::class.java)
    }
}
