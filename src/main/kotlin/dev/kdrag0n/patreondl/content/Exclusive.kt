package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.content.filters.ContentFilter
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileNotFoundException

fun Application.exclusiveModule() {
    routing {
        // Patron-only content (eligibility already verified)
        authenticate("patronSession") {
            val exclusiveSrc = environment.config.property("web.exclusiveSrc").getString()
            val contentFilter = Class.forName(environment.config.property("web.contentFilter").getString())
                .getDeclaredConstructor()
                .newInstance() as ContentFilter

            get("/exclusive/{name}") {
                val name = call.parameters["name"]!!

                withContext(Dispatchers.IO) {
                    // False-positive caused by IOException
                    @Suppress("BlockingMethodInNonBlockingContext")
                    try {
                        FileInputStream("$exclusiveSrc/$name").use { fis ->
                            call.respondOutputStream(ContentType.defaultForFilePath(name)) {
                                contentFilter.writeData(fis, this, environment, call)
                            }
                        }
                    } catch (e: FileNotFoundException) {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val staticSrc = environment.config.propertyOrNull("web.staticSrc")?.getString()
            if (staticSrc != null) {
                static {
                    files(staticSrc)
                }
            }
        }
    }
}