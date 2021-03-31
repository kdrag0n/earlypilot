package dev.kdrag0n.patreondl

import dev.kdrag0n.patreondl.filters.ContentFilter
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

fun Application.exclusiveModule() {
    routing {
        // Patron-only content (eligibility already verified)
        authenticate("patronSession") {
            val exclusiveSrc = environment.config.property("web.exclusiveSrc").getString()
            val contentFilter = Class.forName(environment.config.property("web.contentFilter").getString())
                .getDeclaredConstructor()
                .newInstance() as ContentFilter

            // False-positive caused by IOException
            @Suppress("BlockingMethodInNonBlockingContext")
            get("/exclusive/{name}") {
                val path = call.parameters["name"]
                    ?: return@get call.respondText("Missing", status = HttpStatusCode.BadRequest)

                withContext(Dispatchers.IO) {
                    FileInputStream("$exclusiveSrc/$path").use { fis ->
                        call.respondOutputStream(
                            contentType = ContentType.defaultForFilePath(path)
                        ) {
                            contentFilter.writeData(fis, this, call)
                        }
                    }
                }
            }
        }
    }
}