package dev.kdrag0n.patreondl.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.CurrentTimestamp
import org.jetbrains.exposed.sql.`java-time`.timestamp

object DownloadEvents : IntIdTable("download_events") {
    val accessType = enumerationByName("access_type", 16, AccessType::class)
    val tag = text("tag")
    val fileName = text("file_name")
    val fileHash = text("file_hash").index()
    val downloadTime = timestamp("download_time").defaultExpression(CurrentTimestamp())
    val clientIp = text("client_ip")
    val active = bool("active").default(true)
    val accesses = integer("accesses").default(0)
    val lastAccessTime = timestamp("last_access_time").nullable().default(null)
}

class DownloadEvent(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DownloadEvent>(DownloadEvents) {
        const val HASH_ALGORITHM = "SHA-512"
    }

    var accessType by DownloadEvents.accessType
    var tag by DownloadEvents.tag
    var fileName by DownloadEvents.fileName
    var fileHash by DownloadEvents.fileHash
    var downloadTime by DownloadEvents.downloadTime
    var clientIp by DownloadEvents.clientIp
    var active by DownloadEvents.active
    var accesses by DownloadEvents.accesses
    var lastAccessTime by DownloadEvents.lastAccessTime
}

enum class AccessType {
    USER,
    CREATOR,
    GRANT,
    PURCHASE,
}