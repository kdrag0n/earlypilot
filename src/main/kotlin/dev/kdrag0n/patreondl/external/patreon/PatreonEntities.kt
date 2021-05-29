package dev.kdrag0n.patreondl.external.patreon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant

@Serializable
sealed class PatreonEntity {
    abstract val id: String

    @Serializable
    data class Reference(
        val data: Data,
    ) {
        @Serializable
        data class Data(
            val id: String,
        )
    }
}

@Serializable
@SerialName("user")
class PatreonUser(
    override val id: String,
    val attributes: Attributes,
) : PatreonEntity() {
    @Serializable
    data class Attributes(
        @SerialName("first_name")
        val firstName: String,

        @SerialName("full_name")
        val fullName: String,

        private val created: String,
        @Transient
        val createdAt: Instant = Instant.parse(created),
    )
}

@Serializable
@SerialName("reward")
class PatreonReward(
    override val id: String,
) : PatreonEntity()

@Serializable
@SerialName("campaign")
class PatreonCampaign(
    override val id: String,
) : PatreonEntity()

@Serializable
@SerialName("tier")
class PatreonTier(
    override val id: String,
) : PatreonEntity()

@Serializable
@SerialName("member")
class PatreonMember(
    override val id: String,
    val attributes: Attributes,
    val relationships: Relationships,
) : PatreonEntity() {
    @Serializable
    data class Attributes(
        val email: String,
    )

    @Serializable
    data class Relationships(
        val user: Reference,
    )
}