package dev.kdrag0n.patreondl.data

import dev.kdrag0n.patreondl.security.AuthorizationResult
import dev.kdrag0n.patreondl.splitWhitespace
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : IdTable<String>("users") {
    override val id = text("id").entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }

    val name = text("name")
    val email = text("email")
    val accessToken = text("access_token").nullable()
    val creationTime = timestamp("creation_time")
    val loginTime = timestamp("login_time").nullable().default(null)
    val loginIp = text("login_ip").nullable()
    val activityTime = timestamp("activity_time").nullable().default(null)
    val activityIp = text("activity_ip").nullable()
    val authState = enumerationByName("auth_state", 32, AuthorizationResult::class).nullable()
    val blocked = bool("blocked").default(false)

    // Telegram integration
    val telegramId = long("telegram_id").nullable().default(null)
    val telegramInvite = text("telegram_invite").nullable().default(null).index()
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

    var telegramId by Users.telegramId
    var telegramInvite by Users.telegramInvite

    // This is a very bad assumption to make, but it's what Patreon does and we can't collect any other info here.
    // Referenced in templates
    @Suppress("unused")
    val firstName: String
        get() = name.splitWhitespace()[0]
}
