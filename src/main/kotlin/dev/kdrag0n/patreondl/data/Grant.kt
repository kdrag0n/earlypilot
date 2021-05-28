package dev.kdrag0n.patreondl.data

import io.ktor.auth.*
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp

object Grants : IntIdTable("grants") {
    val type = enumerationByName("type", 32, Grant.Type::class)
    val path = text("path")
    val tag = text("tag")
    val expireTime = timestamp("expire_time")
    val accessCount = integer("access_count").default(0)
    val lastAccessTime = timestamp("last_access_time").nullable().default(null)
    val disabled = bool("disabled").default(false)
}

class Grant(id: EntityID<Int>) : IntEntity(id), Principal {
    companion object : IntEntityClass<Grant>(Grants)

    var type by Grants.type
    var path by Grants.path
    var tag by Grants.tag
    var expireTime by Grants.expireTime
    var accessCount by Grants.accessCount
    var lastAccessTime by Grants.lastAccessTime
    var disabled by Grants.disabled

    enum class Type {
        CREATOR,
        PURCHASE,
    }
}