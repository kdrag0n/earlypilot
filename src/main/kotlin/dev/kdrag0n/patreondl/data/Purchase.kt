package dev.kdrag0n.patreondl.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.CurrentTimestamp
import org.jetbrains.exposed.sql.`java-time`.timestamp

object Purchases : IntIdTable("purchases") {
    val productId = reference("product_id", Products).index()
    val eventId = text("event_id").index()
    val paymentIntentId = text("payment_intent_id")
    val customerId = text("customer_id")
    val quantity = integer("quantity")
    val email = text("email")
    val purchaseTime = timestamp("purchase_time").defaultExpression(CurrentTimestamp())
}

class Purchase(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Purchase>(Purchases)

    var product by Product referencedOn Purchases.productId
    var eventId by Purchases.eventId
    var paymentIntentId by Purchases.paymentIntentId
    var customerId by Purchases.customerId
    var quantity by Purchases.quantity
    var email by Purchases.email
    var purchaseTime by Purchases.purchaseTime
}