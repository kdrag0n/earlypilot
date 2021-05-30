package dev.kdrag0n.patreondl

import com.github.mustachejava.DefaultMustacheFactory
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.content.filters.ContentFilter
import dev.kdrag0n.patreondl.external.email.Mailer
import dev.kdrag0n.patreondl.external.maxmind.GeoipService
import dev.kdrag0n.patreondl.external.stripe.CheckoutManager
import dev.kdrag0n.patreondl.external.telegram.TelegramBot
import dev.kdrag0n.patreondl.external.telegram.TelegramInviteManager
import dev.kdrag0n.patreondl.external.patreon.PatreonApi
import dev.kdrag0n.patreondl.payments.PriceManager
import dev.kdrag0n.patreondl.security.GrantManager
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.mustache.*
import io.ktor.serialization.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.inject
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level
import java.text.NumberFormat

fun Application.featuresModule() {
    // Dependency injection
    install(Koin) {
        slf4jLogger()

        val mainModule = module {
            // Core
            single { environment }
            single { Config.fromFile(environment.config.property("app.configPath").getString()) }

            // Third-party
            single { HttpClient(Apache) }
            single { NumberFormat.getCurrencyInstance() }

            // API clients
            single { PatreonApi() }
            single { Mailer(get()) }
            single { TelegramBot(get()) }
            single { CheckoutManager(get(), get(), get()) }
            single { GeoipService(get()) }

            // Logic
            single { TelegramInviteManager(get(), get(), get()) }
            single { ContentFilter.createByName(get(), get(), get<Config>().content.exclusiveFilter) }
            single { GrantManager(get()) }
            single { PriceManager(get(), get(), get()) }
        }

        modules(mainModule)
    }
    val config: Config by inject()

    // Typed routes
    install(Locations)

    // Reverse proxy support (X-Forwarded-*)
    if (config.web.forwardedHeaders) {
        install(XForwardedHeaderSupport) {
            // Any unused header is a security issue.
            // TODO: file Ktor issue to avoid using reflection
            javaClass.getDeclaredField("hostHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf(HttpHeaders.XForwardedHost))
            }
            javaClass.getDeclaredField("protoHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf(HttpHeaders.XForwardedProto))
            }
            javaClass.getDeclaredField("forHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf(HttpHeaders.XForwardedFor))
            }
            javaClass.getDeclaredField("httpsFlagHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf<String>())
            }
        }
    }

    // In case users download with a parallel downloader, e.g. aria2
    install(PartialContent) {
        maxRangeCount = 10
    }

    // Support HEAD for completeness
    install(AutoHeadResponse)

    // Support CORS (e.g. for web installer downloads)
    install(CORS) {
        // Send cookies for session authentication
        allowCredentials = true

        val schemes = if (config.web.httpsOnly) listOf("https") else listOf("https", "http")
        for (host in config.web.corsAllowed) {
            host(host, schemes = schemes)
        }
    }

    // Request logging
    install(CallLogging) {
        level = Level.INFO
    }

    // Templating
    install(Mustache) {
        mustacheFactory = DefaultMustacheFactory("templates")
    }

    // JSON API
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}