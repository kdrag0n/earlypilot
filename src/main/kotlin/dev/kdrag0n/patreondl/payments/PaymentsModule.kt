package dev.kdrag0n.patreondl.payments

import com.google.gson.JsonSyntaxException
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.Product
import dev.kdrag0n.patreondl.data.Products
import dev.kdrag0n.patreondl.external.stripe.CheckoutManager
import dev.kdrag0n.patreondl.respondErrorPage
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.mustache.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.text.NumberFormat

fun Application.paymentsModule() {
    val config: Config by inject()
    val checkoutManager: CheckoutManager by inject()
    val priceManager: PriceManager by inject()
    val currencyFormat: NumberFormat by inject()

    Stripe.apiKey = config.external.stripe.secretKey

    routing {
        get("/buy/exclusive/{itemPath}") {
            val itemPath = call.parameters["itemPath"]!!

            val product = newSuspendedTransaction {
                Product.find { Products.path eq itemPath }
                    .firstOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            // Public products are no longer available for purchase
            if (!product.active) {
                if (product.publicUrl != null) {
                    call.respondRedirect(product.publicUrl!!, permanent = false)
                } else {
                    call.respondErrorPage(
                        HttpStatusCode.Gone,
                        "No longer available for purchase",
                        "This product is no longer available purchase, likely because it is now public.",
                    )
                }

                return@get
            }

            val priceCents = priceManager.getPriceForProduct(product, call.request.origin.remoteHost)
            val normalizedImageUrl = if (product.imageUrl?.startsWith("/") == true) {
                call.url {
                    // Remove leading slash
                    path(product.imageUrl!!.substring(1))
                }
            } else {
                product.imageUrl
            }

            call.respond(MustacheContent("checkout.hbs", mapOf(
                "product" to product,
                "config" to config,
                "formattedPrice" to currencyFormat.format(priceCents.toDouble() / 100),
                "requestUrl" to call.url(),
                "imageUrl" to normalizedImageUrl,
            )))
        }

        post("/api/stripe/create_checkout_session") {
            val productReq = call.receive<CreateCheckoutSessionRequest>()
            val product = newSuspendedTransaction {
                Product.findById(productReq.productId)
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val price = priceManager.getPriceForProduct(product, call.request.origin.remoteHost)
            val session = checkoutManager.createSession(product, price)
            call.respond(CreateCheckoutSessionResponse(
                sessionId = session.id,
            ))
        }

        post("/_webhooks/stripe/${config.external.stripe.webhookKey}") {
            val payload = call.receiveText()
            val event = try {
                Webhook.constructEvent(
                    payload,
                    call.request.header("Stripe-Signature")
                        ?: return@post call.respond(HttpStatusCode.BadRequest),
                    config.external.stripe.webhookSecret,
                )
            } catch (e: JsonSyntaxException) {
                return@post call.respond(HttpStatusCode.BadRequest)
            } catch (e: SignatureVerificationException) {
                return@post call.respond(HttpStatusCode.BadRequest)
            }

            when (event.type) {
                "checkout.session.completed" -> {
                    val session = event.dataObjectDeserializer.`object`.get() as Session
                    checkoutManager.fulfillPurchase(session)
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}

@Serializable
data class CreateCheckoutSessionRequest(
    val productId: Int,
)

@Serializable
data class CreateCheckoutSessionResponse(
    val sessionId: String,
)
