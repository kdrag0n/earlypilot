package dev.kdrag0n.patreondl.content

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.io.File

/*
 * This code was copied from upstream Ktor and modified to support fetching indexes in directory roots.
 * All this code needs to be duplicated due to private access modifiers.
 * TODO: clean up and submit to upstream Ktor
 */

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

private const val pathParameterName = "static-content-path-parameter"

private val compressedKey = AttributeKey<List<CompressedFileType>>("StaticContentCompressed")

private val Route.staticContentEncodedTypes: List<CompressedFileType>?
    get() = attributes.getOrNull(compressedKey) ?: parent?.staticContentEncodedTypes

private fun File.bestCompressionFit(
    acceptEncoding: List<HeaderValue>,
    compressedTypes: List<CompressedFileType>?
): CompressedFileType? {
    val acceptedEncodings = acceptEncoding.map { it.value }.toSet()
    // We respect the order in compressedTypes, not the one on Accept header
    return compressedTypes?.filter {
        it.encoding in acceptedEncodings
    }?.firstOrNull { it.file(this).isFile }
}

private class PreCompressedResponse(
    val original: ReadChannelContent,
    val encoding: String?,
) : OutgoingContent.ReadChannelContent() {
    override val contentLength get() = original.contentLength
    override val contentType get() = original.contentType
    override val status get() = original.status
    override fun readFrom() = original.readFrom()
    override fun readFrom(range: LongRange) = original.readFrom(range)
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        if (encoding != null) {
            Headers.build {
                appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                append(HttpHeaders.ContentEncoding, encoding)
            }
        } else original.headers
    }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
}

private suspend inline fun ApplicationCall.respondStaticFile(
    requestedFile: File,
    compressedTypes: List<CompressedFileType>?
) {
    val bestCompressionFit = requestedFile.bestCompressionFit(request.acceptEncodingItems(), compressedTypes)
    bestCompressionFit?.run {
        attributes.put(Compression.SuppressionAttribute, true)
    }
    val localFile = bestCompressionFit?.file(requestedFile) ?: requestedFile
    if (localFile.isFile) {
        val localFileContent = LocalFileContent(localFile, ContentType.defaultForFile(requestedFile))
        respond(PreCompressedResponse(localFileContent, bestCompressionFit?.encoding))
    }
}

@KtorExperimentalAPI
private suspend inline fun PipelineContext<Unit, ApplicationCall>.respondFiles(
    dir: File,
    compressedTypes: List<CompressedFileType>?,
    index: String
) {
    val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return
    val file = dir.combineSafe(relativePath)
    val respFile = if (file.isDirectory) file.combineSafe(index) else file
    call.respondStaticFile(respFile, compressedTypes)
}

@KtorExperimentalAPI
fun Route.filesWithIndex(folder: File, index: String) {
    val dir = staticRootFolder.combine(folder)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        respondFiles(dir, compressedTypes, index)
    }
    get("{$pathParameterName...}/") {
        respondFiles(dir, compressedTypes, index)
    }
}

@KtorExperimentalAPI
fun Route.filesWithIndex(folder: String, index: String): Unit = filesWithIndex(File(folder), index)