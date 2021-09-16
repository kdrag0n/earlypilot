package dev.kdrag0n.patreondl.external.stripe

import com.stripe.model.Charge
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.checkout.SessionListLineItemsParams
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.*
import dev.kdrag0n.patreondl.external.email.EmailTemplates
import dev.kdrag0n.patreondl.external.email.EmailTemplates.Companion.execute
import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.security.GrantManager
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class CheckoutManager(
    private val config: Config,
    private val mailer: Mailer,
    private val grantManager: GrantManager,
    private val emailTemplates: EmailTemplates,
) {
    suspend fun createSession(
        product: Product,
        priceCents: Int,
    ): Session {
        val txRefId = UUID.randomUUID().toString()

        val params = SessionCreateParams.builder().run {
            setMode(SessionCreateParams.Mode.PAYMENT)
            addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            setSuccessUrl("${config.web.baseUrl}/buy/success/$txRefId")
            setCancelUrl(config.content.benefitIndexUrl)

            putMetadata("productId", product.id.value.toString())
            putMetadata("txRefId", txRefId)

            setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder().run {
                setDescription("One-time purchase of ${product.name}")
                build()
            })

            addLineItem(SessionCreateParams.LineItem.builder().run {
                setQuantity(1)
                setAdjustableQuantity(SessionCreateParams.LineItem.AdjustableQuantity.builder().run {
                    setEnabled(true)
                    setMinimum(1)
                    setMaximum(99)
                    build()
                })

                setPriceData(SessionCreateParams.LineItem.PriceData.builder().run {
                    setCurrency("usd")
                    setUnitAmount(priceCents.toLong())
                    setAllowPromotionCodes(true)

                    setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().run {
                        setName(product.name)
                        setDescription("One-time early access purchase")
                        build()
                    })

                    build()
                })

                build()
            })

            build()
        }

        return withContext(Dispatchers.IO) {
            Session.create(params)
        }
    }

    private suspend fun createGrantLinks(
        quantity: Long,
        purchaseId: Int,
        targetPath: String,
    ) = (1..quantity).map { idx ->
        val url = grantManager.createGrantUrl(
            targetPath = "/$targetPath",
            tag = "$purchaseId",
            type = Grant.Type.PURCHASE,
            // 1 month
            durationHours = 31f * 24,
        )

        if (quantity > 1) {
            "Link $idx: $url"
        } else {
            url
        }
    }

    suspend fun fulfillPurchase(session: Session) {
        val quantity = session.getQuantity()
        val email = session.customerEmail ?: session.customerDetails.email

        val (purchase, product) = newSuspendedTransaction {
            // Missing product ID = purchase was tampered with or didn't come from this server
            val productId = session.metadata["productId"]?.toInt()
                ?: return@newSuspendedTransaction null

            // Transaction reference ID for web flow
            val txRefId = UUID.fromString(session.metadata["txRefId"])

            val product = Product.findById(productId)
                ?: error("Stripe returned invalid product ID $productId")

            val purchase = Purchase.find { Purchases.eventId eq session.id }.limit(1).firstOrNull()
                ?: Purchase.new {
                    this.product = product
                    eventId = session.id
                    paymentIntentId = session.paymentIntent
                    customerId = session.customer
                    this.quantity = quantity.toInt()
                    this.email = email
                    this.txRefId = txRefId
                }

            purchase to product
        } ?: error("Invalid metadata in payment intent")

        // Idempotency: bail out if event has already been fulfilled successfully
        if (purchase.fulfilled) {
            return
        }

        val grantUrls = newSuspendedTransaction {
            // Find grants and generate links if they already exist (for a fulfillment retry)
            val existingUrls = Grant.find { (Grants.type eq Grant.Type.PURCHASE) and (Grants.tag eq purchase.id.value.toString()) }
                .orderBy(Grants.id to SortOrder.ASC)
                .limit(quantity.toInt())
                .map { grantManager.generateGrantUrl(it) }
            if (existingUrls.size > 1) {
                return@newSuspendedTransaction existingUrls.withIndex()
                    .map { (idx, url) -> "Link ${idx + 1}: $url" }
            } else if (existingUrls.isNotEmpty()) {
                return@newSuspendedTransaction existingUrls
            }

            // Create new grants and links if they don't already exist
            val targetPath = "exclusive/${product.path}"
            newSuspendedTransaction {
                createGrantLinks(quantity, purchase.id.value, targetPath)
            }
        }

        val messageTemplate = if (quantity > 1) {
            emailTemplates.multiPurchaseSuccessful
        } else {
            emailTemplates.singlePurchaseSuccessful
        }

        // Generate email
        val messageText = messageTemplate.execute(mapOf(
            "product" to product,
            "downloadUrls" to grantUrls.joinToString(LIST_SEPARATOR),
        ))

        // Send email
        mailer.sendEmail(
            toAddress = email,
            toName = null,
            subject = "Thank you for purchasing ${product.name}!",
            bodyText = messageText,
        )

        // Mark purchase as fulfilled if everything else succeeded
        newSuspendedTransaction {
            Purchase.findById(purchase.id.value)!!.apply {
                fulfilled = true
            }
        }
    }

    suspend fun refundCharge(charge: Charge) {
        if (!charge.refunded || charge.paymentIntent == null) {
            return
        }

        newSuspendedTransaction {
            val purchase = Purchase.find { Purchases.paymentIntentId eq charge.paymentIntent }
                .limit(1).firstOrNull()
                ?: return@newSuspendedTransaction

            // Revoke grant
            Grant.find { (Grants.type eq Grant.Type.PURCHASE) and (Grants.tag eq purchase.id.toString()) }.forEach {
                it.disabled = true
            }

            // Revoke downloads
            val downloads = DownloadEvent
                .find { (DownloadEvents.accessType eq AccessType.PURCHASE) and (DownloadEvents.tag eq purchase.id.toString()) }
            downloads.forEach {
                it.active = false
            }
        }
    }

    companion object {
        private const val LIST_SEPARATOR = "\n    • "

        private suspend fun Session.getQuantity() = withContext(Dispatchers.IO) {
            listLineItems(SessionListLineItemsParams.builder().run {
                setLimit(99)
                build()
            }).data[0].quantity
        }
    }
}
