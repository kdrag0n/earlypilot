package dev.kdrag0n.patreondl

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.auth.*
import io.ktor.util.*
import io.ktor.locations.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.serialization.*
import io.ktor.sessions.*
import io.ktor.auth.jwt.*
import io.ktor.application.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.request.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

/**
 * Please note that you can use any other name instead of *module*.
 * Also note that you can have more then one modules in your application.
 * */
@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(production: Boolean = false) {
    // Typed routes
    install(Locations) {}

    install(ContentNegotiation) {
        json()
    }

    install(PartialContent) {
        maxRangeCount = 10
    }

    install(Sessions) {
        cookie<PatronSession>("PATRON_SESSION") {
            cookie.extensions["SameSite"] = "Strict"
            cookie.secure = production
        }
    }

    install(AutoHeadResponse)
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header("MyCustomHeader")
        allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

    val loginProviders = listOf(
        OAuthServerSettings.OAuth2ServerSettings(
            name = "patreon",
            authorizeUrl = "https://www.patreon.com/oauth2/authorize",
            accessTokenUrl = "https://www.patreon.com/api/oauth2/token",
            clientId = "***",
            clientSecret = "***",
            requestMethod = HttpMethod.Post,
        )
    ).associateBy { it.name }

    val httpClient = HttpClient(Apache)

    authentication {
        oauth("patreonOAuth") {
            client = httpClient
            providerLookup = { loginProviders[application.locations.resolve<login>(login::class, this).type] }
            urlProvider = { url(login(it.name)) }
        }

        session<PatronSession>("patronSession") {
            challenge("/login/patreon")
            validate { session -> session }
        }
    }

    routing {
        get<MyLocation> {
            call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}")
        }
        // Register nested routes
        get<Type.Edit> {
            call.respondText("Inside $it")
        }
        get<Type.List> {
            call.respondText("Inside $it")
        }

        authenticate("patreonOAuth") {
            location<login>() {
                param("error") {
                    handle {
                        // TODO: call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                    }
                }

                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse>()
                    if (principal !is OAuthAccessTokenResponse.OAuth2) {
                        return@handle
                    }

                    call.sessions.set(PatronSession(principal.accessToken))
                    call.respondText("Access Token = ${principal.accessToken}")
                }
            }
        }

        authenticate("patronSession") {
            static("exclusive") {
                files("exclusive_src")
            }
        }
    }
}

@Location("/location/{name}")
class MyLocation(val name: String, val arg1: Int = 42, val arg2: String = "default")
@Location("/type/{name}")
data class Type(val name: String) {
    @Location("/edit")
    data class Edit(val type: Type)

    @Location("/list/{page}")
    data class List(val type: Type, val page: Int)
}

@Location("/login/{type?}")
class login(val type: String = "")
data class PatronSession(
    val accessToken: String,
) : Principal