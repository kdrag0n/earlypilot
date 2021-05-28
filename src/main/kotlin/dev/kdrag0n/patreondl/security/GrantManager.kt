package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.Grant
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.codec.binary.Base64
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class GrantManager(
    config: Config,
) {
    private val encrypter = AuthenticatedEncrypter(hex(config.web.grantKey))

    suspend fun generateGrantKey(
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

        val grantInfo = GrantInfo(
            grantId = grant.id.value,
        )

        // Pad to nearest 8-byte boundary to avoid side-channel attacks
        var grantJson = Json.encodeToString(grantInfo)
        grantJson += " ".repeat(grantJson.length % 8)
        // Encrypt padded JSON data
        return Base64.encodeBase64String(encrypter.encrypt(grantJson.encodeToByteArray()))
    }

    suspend fun generateGrantUrl(
        call: ApplicationCall,
        tag: String,
        type: Grant.Type,
        durationHours: Float,
    ): String {
        val path = call.request.path()
        val grantKey = generateGrantKey(path, tag, type, durationHours)

        return call.url {
            parameters.clear()
            parameters["grant"] = grantKey
        }
    }
}