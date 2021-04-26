package dev.kdrag0n.patreondl.data

import dev.kdrag0n.patreondl.security.AuthorizationResult
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.`java-time`.CurrentTimestamp
import org.jetbrains.exposed.sql.`java-time`.timestamp

object Users : IdTable<String>("users") {
    override val id = text("id").entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }

    val name = text("name")
    val email = text("email")
    val accessToken = text("access_token")
    val creationTime = timestamp("creation_time")
    val loginTime = timestamp("login_time").defaultExpression(CurrentTimestamp())
    val loginIp = text("login_ip")
    val activityTime = timestamp("activity_time").defaultExpression(CurrentTimestamp())
    val activityIp = text("activity_ip").nullable()
    val authState = enumerationByName("auth_state", 32, AuthorizationResult::class).nullable()
    val blocked = bool("blocked").default(false)
}

class User(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, User>(Users)

    var name by Users.name
    var email by Users.email
    var accessToken by Users.accessToken
    var creationTime by Users.creationTime
    var loginTime by Users.loginTime
    var loginIp by Users.loginIp
    var activityTime by Users.activityTime
    var activityIp by Users.activityIp
    var authState by Users.authState
    var blocked by Users.blocked
}