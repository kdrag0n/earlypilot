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
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.mustache.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

fun Application.paymentsModule() {
    val config: Config by inject()
    val checkoutManager: CheckoutManager by inject()

    Stripe.apiKey = config.external.stripe.secretKey

    routing {
        get("/buy/exclusive/{itemPath}") {
            val itemPath = call.parameters["itemPath"]!!

            val product = newSuspendedTransaction {
                Product.find { Products.path eq itemPath }
                    .firstOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(MustacheContent("checkout.hbs", mapOf(
                "product" to product,
                "config" to config,
            )))
        }

        post("/api/stripe/create_checkout_session") {
            val productReq = call.receive<CreateCheckoutSessionRequest>()
            val product = newSuspendedTransaction {
                Product.findById(productReq.productId)
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val session = checkoutManager.createSession(product)
            call.respond(CreateCheckoutSessionResponse(
                sessionId = session.id,
            ))
        }

        post("/api/stripe/webhook") {
            val payload = call.receiveText()
            println(payload)
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