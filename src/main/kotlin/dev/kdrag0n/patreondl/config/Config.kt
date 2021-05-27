package dev.kdrag0n.patreondl.config

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

data class Config(
    val web: Web,
    val content: Content,
    val database: Database,
    val external: External,
) {
    data class Web(
        val corsAllowed: List<String>,
        val httpsOnly: Boolean,
        val forwardedHeaders: Boolean,

        // Secrets
        val sessionEncryptKey: String,
        val sessionAuthKey: String,
        val grantKey: String,
    )

    data class Content(
        val benefitIndexUrl: String,

        val exclusiveSrc: String,
        val staticSrc: String,

        val exclusiveFilter: String,
    )

    data class Database(
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String?,
    )

    data class External(
        val patreon: Patreon,
        val email: Email,
        val telegram: Telegram,
    ) {
        data class Patreon(
            // API
            val clientId: String,
            val clientSecret: String,
            val webhookKey: String,

            // Creator info
            val creatorId: String,
            val creatorName: String,

            // User requirements
            val minTierAmount: Int,
        )

        data class Email(
            val apiKey: String,
            val fromAddress: String,

            val messageTemplates: MessageTemplates,
        ) {
            data class MessageTemplates(
                val telegramWelcome: String,
            )
        }

        data class Telegram(
            val botToken: String,
            val groupId: Long,
        )
    }

    companion object {
        fun fromFile(path: String) = ConfigLoader().loadConfigOrThrow<Config>(File(path))
    }
}