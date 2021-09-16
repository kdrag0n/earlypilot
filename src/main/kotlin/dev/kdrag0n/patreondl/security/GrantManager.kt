package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.Grant
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.codec.binary.Base64
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class GrantManager(
    private val config: Config,
) {
    private val encrypter = AuthenticatedEncrypter(hex(config.web.grantKey))

    private fun generateGrantKey(grant: Grant): String {
        val grantInfo = GrantInfo(
            grantId = grant.id.value,
        )

        // Pad to nearest 8-byte boundary to avoid side-channel attacks
        var grantJson = Json.encodeToString(grantInfo)
        grantJson += " ".repeat(grantJson.length % 8)
        // Encrypt padded JSON data
        return Base64.encodeBase64String(encrypter.encrypt(grantJson.encodeToByteArray()))
    }

    private suspend fun createGrantKey(
        targetPath: String,
        tag: String,
        type: Grant.Type,
        durationHours: Float,
    ): String {
        val durationMs = (durationHours * 60 * 60 * 1000).toLong()
        val grant = newSuspendedTransaction(Dispatchers.IO) {
            Grant.new {
                path = targetPath
                this.tag = tag
                this.type = type
                expireTime = Instant.now().plusMillis(durationMs)
            }
        }

        return generateGrantKey(grant)
    }

    private fun generateRawGrantUrl(
        targetPath: String,
        grantKey: String,
    ): String {
        return URLBuilder(config.web.baseUrl).apply {
            path(targetPath.trimStart('/'))
            parameters["grant"] = grantKey
        }.buildString()
    }

    fun generateGrantUrl(grant: Grant): String {
        val grantKey = generateGrantKey(grant)
        return generateRawGrantUrl(grant.path, grantKey)
    }

    suspend fun createGrantUrl(
        targetPath: String,
        tag: String,
        type: Grant.Type,
        durationHours: Float,
    ): String {
        val grantKey = createGrantKey(targetPath, tag, type, durationHours)
        return generateRawGrantUrl(targetPath, grantKey)
    }
}
