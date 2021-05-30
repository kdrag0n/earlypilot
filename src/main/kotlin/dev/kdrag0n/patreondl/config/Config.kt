package dev.kdrag0n.patreondl.config

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

data class Config(
    val web: Web,
    val content: Content,
    val payments: Payments,
    val database: Database,
    val external: External,
) {
    data class Web(
        val baseUrl: String,

        // Security
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

        // Content sources
        val exclusiveSrc: String,
        val staticSrc: String,

        val exclusiveFilter: String,
    )

    data class Payments(
        val successUrl: String,
        val defaultPriceCents: Int,
        val usePriceParity: Boolean,
        val minPriceCents: Int,
    )

    data class Database(
        // PostgreSQL credentials
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
        val stripe: Stripe,
        val sentry: Sentry?,
        val maxmind: MaxMind,
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
                val singlePurchaseSuccessful: String,
                val multiPurchaseSuccessful: String,
            )
        }

        data class Telegram(
            val botToken: String,
            val groupId: Long,
        )

        data class Stripe(
            val publicKey: String,
            val secretKey: String,
            val webhookSecret: String,
            val webhookKey: String,
        )

        data class Sentry(
            val dsn: String,
        )

        data class MaxMind(
            val databasePath: String,
        )
    }

    companion object {
        fun fromFile(path: String) = ConfigLoader().loadConfigOrThrow<Config>(File(path))
    }
}