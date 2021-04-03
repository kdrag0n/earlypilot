package dev.kdrag0n.patreondl.security

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

enum class AuthorizationResult {
    SUCCESS,

    // Server issues
    API_ERROR,

    // User issues
    NO_PLEDGE,
    LOW_TIER,
    PAYMENT_DECLINED;

    companion object {
        val KEY = AttributeKey<AuthorizationResult>("authorization.result")
    }
}

suspend fun ApplicationCall.respondAuthorizationResult(
    creatorName: String,
    creatorId: String,
    minTierAmount: Int,
) {
    val creatorUrl = "https://patreon.com/user?u=$creatorId"
    val minTierAmountPretty = "$" + String.format("%.02f", minTierAmount.toFloat() / 100)

    when (attributes[AuthorizationResult.KEY]) {
        AuthorizationResult.API_ERROR -> respondText(
            """
                We’re having trouble talking to Patreon. Please try again later.
                Sorry for the inconvenience!
            """.trimIndent(),
            status = HttpStatusCode.InternalServerError
        )
        AuthorizationResult.NO_PLEDGE -> respondText(
            """
                You can’t access benefits because you haven’t pledged to $creatorName on Patreon.
                Pledge at least $minTierAmountPretty per month here: $creatorUrl
            """.trimIndent(),
            status = HttpStatusCode.PaymentRequired
        )
        AuthorizationResult.LOW_TIER -> respondText(
            """
                You can‘t access benefits because your pledge’s tier is too low.
                Pledge at least $minTierAmountPretty per month here: $creatorUrl
            """.trimIndent(),
            status = HttpStatusCode.PaymentRequired
        )
        AuthorizationResult.PAYMENT_DECLINED -> respondText(
            """
                You can‘t access benefits because your payment was declined.
                Please fix the issue on Patreon and try again after 2 hours.
            """.trimIndent(),
            status = HttpStatusCode.PaymentRequired
        )

        // All other results should redirect back to login
        else -> respondRedirect("/login")
    }
}