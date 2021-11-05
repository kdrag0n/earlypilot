package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.respondErrorPage
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

enum class AuthorizationResult {
    SUCCESS,

    // Server issues
    API_ERROR,

    // User issues
    TOKEN_EXPIRED,
    NO_PLEDGE,
    LOW_TIER,
    PAYMENT_DECLINED,
    BLOCKED;

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

    when (attributes.getOrNull(AuthorizationResult.KEY)) {
        AuthorizationResult.API_ERROR -> respondErrorPage(
            HttpStatusCode.InternalServerError,
            "Patreon error",
            """
                We’re having trouble talking to Patreon. Please try again later.
                Sorry for the inconvenience!
            """.trimIndent(),
        )
        AuthorizationResult.NO_PLEDGE -> respondErrorPage(
            HttpStatusCode.PaymentRequired,
            "No pledge found",
            """
                You can’t access benefits because you haven’t pledged to $creatorName on Patreon.
                <a href="$creatorUrl">Pledge at least $minTierAmountPretty per month</a> to get access.
            """.trimIndent()
        )
        AuthorizationResult.LOW_TIER -> respondErrorPage(
            HttpStatusCode.PaymentRequired,
            "Tier is too low",
            """
                You can‘t access benefits because your pledge’s tier is too low.
                <a href="$creatorUrl">Pledge at least $minTierAmountPretty per month</a> to get access.
            """.trimIndent()
        )
        AuthorizationResult.PAYMENT_DECLINED -> respondErrorPage(
            HttpStatusCode.PaymentRequired,
            "Payment declined",
            """
                You can‘t access benefits because your payment was declined.
                Please fix the issue on Patreon and try again.
            """.trimIndent()
        )
        AuthorizationResult.BLOCKED -> respondErrorPage(
            HttpStatusCode.PaymentRequired,
            "Blocked",
            """
                You have been blocked from accessing download benefits.
                Please reach out to <a href="$creatorUrl">$creatorName</a> in order to resolve this issue. 
            """.trimIndent()
        )

        // All other results should redirect back to login
        else -> {
            // Prevent browser from caching the redirect and creating an auth redirect loop
            response.headers.append(HttpHeaders.CacheControl, "no-store")
            respondRedirect("/login")
        }
    }
}
