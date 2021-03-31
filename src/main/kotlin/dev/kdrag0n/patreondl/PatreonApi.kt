package dev.kdrag0n.patreondl

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.beust.klaxon.TypeAdapter
import com.beust.klaxon.TypeFor
import com.patreon.PatreonAPI
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

@TypeFor(field = "type", adapter = PatreonTypeAdapter::class)
sealed class PatreonEntity(val type: String) {
    data class Reference(
        val id: String,
    )

    data class References(
        val data: List<Reference>
    )
}

data class User(
    val id: String,
    val attributes: Attributes,
    val relationships: Relationships,
) : PatreonEntity("user") {
    @Json(ignored = true)
    lateinit var pledge: Pledge

    data class Attributes(
        @Json(name = "full_name")
        val fullName: String,

        @Json(name = "is_suspended")
        val isSuspended: Boolean,

        @Json(name = "is_deleted")
        val isDeleted: Boolean,

        @Json(name = "is_email_verified")
        val isEmailVerified: Boolean,

        val email: String,
    )

    data class Relationships(
        val pledges: References,
    )
}

data class Pledge(
    val id: String,
    val attributes: Attributes,
) : PatreonEntity("pledge") {
    data class Attributes(
        @Json(name = "amount_cents")
        val amountCents: Int,

        @Json(name = "declined_since")
        val declinedSince: String?,
    )
}

data class Reward(
    val id: String,
    val attributes: Attributes,
) : PatreonEntity("reward") {
    data class Attributes(
        @Json(name = "amount_cents")
        val amountCents: Int,
    )
}

class PatreonTypeAdapter : TypeAdapter<PatreonEntity> {
    override fun classFor(type: Any): KClass<out PatreonEntity> = when (type as String) {
        "user" -> User::class
        "pledge" -> Pledge::class
        "reward" -> Reward::class
        else -> error("Unknown type: $type")
    }
}

class PatreonApi(
    private val client: HttpClient,
) {
    private val klaxon = Klaxon()

    suspend fun getIdentity(token: String): com.patreon.resources.User {
        return withContext(Dispatchers.IO) {
            // False-positive caused by IOException
            @Suppress("BlockingMethodInNonBlockingContext")
            PatreonAPI(token).fetchUser().get()
                ?: error("Failed to fetch Patreon user info")
        }
    }

    data class Response(
        val data: PatreonEntity,
        val included: List<PatreonEntity>,
    )
}