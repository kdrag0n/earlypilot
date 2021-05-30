package dev.kdrag0n.patreondl.external.stripe

import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.checkout.SessionListLineItemsParams
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.Grant
import dev.kdrag0n.patreondl.data.Product
import dev.kdrag0n.patreondl.data.Purchase
import dev.kdrag0n.patreondl.data.Purchases
import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.payments.PriceManager
import dev.kdrag0n.patreondl.security.GrantManager
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class CheckoutManager(
    private val config: Config,
    private val mailer: Mailer,
    private val grantManager: GrantManager,
) {
    suspend fun createSession(
        product: Product,
        priceCents: Int,
    ): Session {
        val params = SessionCreateParams.builder().run {
            setMode(SessionCreateParams.Mode.PAYMENT)
            addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            addPaymentMethodType(SessionCreateParams.PaymentMethodType.ALIPAY)
            setSuccessUrl(config.payments.successUrl)
            setCancelUrl(config.content.benefitIndexUrl)
            putMetadata("productId", product.id.value.toString())

            setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder().run {
                setDescription("One-time purchase of ${product.name} (product ID ${product.id})")
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

    private suspend fun generateGrantLinks(
        quantity: Long,
        purchaseId: Int,
        targetPath: String,
    ) = (1..quantity).map { idx ->
        val grantKey = grantManager.generateGrantKey(
            targetPath = "/$targetPath",
            tag = "$purchaseId",
            type = Grant.Type.PURCHASE,
            // 1 month
            durationHours = 31f * 24,
        )

        val url = URLBuilder(config.web.baseUrl).apply {
            path(targetPath)
            parameters["grant"] = grantKey
        }.buildString()

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
            val productId = session.metadata["productId"]!!.toInt()
            val product = Product.findById(productId)
                ?: error("Stripe returned invalid product ID $productId")

            // Idempotency: don't process duplicate events
            if (!Purchase.find { Purchases.eventId eq session.id }.empty()) {
                return@newSuspendedTransaction null to product
            }

            val purchase = Purchase.new {
                this.product = product
                eventId = session.id
                paymentIntentId = session.paymentIntent
                customerId = session.customer
                this.quantity = quantity.toInt()
                this.email = email
            }

            purchase to product
        }

        // Idempotency: bail out if event is a duplicate
        if (purchase == null) {
            return
        }

        // Generate grants
        val targetPath = "exclusive/${product.path}"
        val grantUrls = generateGrantLinks(quantity, purchase.id.value, targetPath)
        val messageTemplate = if (quantity > 1) {
            config.external.email.messageTemplates.multiPurchaseSuccessful
        } else {
            config.external.email.messageTemplates.singlePurchaseSuccessful
        }

        // Generate email
        val messageText = messageTemplate
            .replace("[PRODUCT_NAME]", product.name)
            .replace("[DOWNLOAD_URLS]", grantUrls.joinToString(LIST_SEPARATOR))

        // Send email
        mailer.sendEmail(
            toAddress = email,
            toName = null,
            subject = "Thank you for purchasing ${product.name}!",
            bodyText = messageText,
        )
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