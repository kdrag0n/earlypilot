package dev.kdrag0n.patreondl.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

// Copy of OutputStreamContent from Ktor, with Content-Length support
class OutputStreamLengthContent(
    private val body: suspend OutputStream.() -> Unit,
    override val contentLength: Long?,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withContext(Dispatchers.IO) {
            // use block should be inside because closing OutputStream is blocking as well
            // and should not be invoked in a epoll/kqueue/reactor thread
            channel.toOutputStream().use { stream ->
                stream.body()
            }
        }
    }
}

suspend fun ApplicationCall.respondOutputStreamWithLength(
    length: Long? = null,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    producer: suspend OutputStream.() -> Unit
) {
    val message = OutputStreamLengthContent(producer, length, contentType ?: ContentType.Application.OctetStream, status)
    respond(message)
}
