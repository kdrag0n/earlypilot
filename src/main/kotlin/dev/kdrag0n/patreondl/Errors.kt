package dev.kdrag0n.patreondl

import dev.kdrag0n.patreondl.config.Config
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.sentry.Sentry
import org.koin.ktor.ext.inject

fun Application.errorsModule() {
    val config: Config by inject()

    if (config.external.sentry != null) {
        Sentry.init { options ->
            options.dsn = config.external.sentry!!.dsn
        }
    }

    install(StatusPages) {
        status(HttpStatusCode.BadRequest) { call, status -> genericStatusError(call, status) }
        status(HttpStatusCode.Unauthorized) { call, status -> genericStatusError(call, status) }
        status(HttpStatusCode.Forbidden) { call, status -> genericStatusError(call, status) }
        status(HttpStatusCode.NotFound) { call, status -> genericStatusError(call, status) }

        exception<Throwable> { call, error ->
            this@errorsModule.environment.log.error("Uncaught exception in route", error)
            Sentry.captureException(error)

            genericStatusError(call, HttpStatusCode.InternalServerError)
        }
    }
}

private suspend fun genericStatusError(call: ApplicationCall, status: HttpStatusCode) {
    call.respondErrorPage(status, "${status.value} ${status.description}")
}

suspend fun ApplicationCall.respondErrorPage(status: HttpStatusCode, title: String, description: String = "") {
    val html = """
        <html>
            <head>
                <meta charset="UTF-8">
                <title>$title</title>
                <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
                <style>
                    body {
                        font-family: system-ui, Roboto, sans-serif, serif;
                    }
                </style>
            </head>

            <body>
                <h1>$title</h1>
                <p>${description.replace("\n", "<br>")}</p>
            </body>
        </html>
    """.trimIndent()

    respondText(html, status = status, contentType = ContentType.Text.Html)
}
