package dev.kdrag0n.patreondl.external.patreon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PatreonEntity {
    abstract val id: String
}

@Serializable
@SerialName("user")
class PatreonUser(
    override val id: String,
    val attributes: Attributes,
) : PatreonEntity() {
    @Serializable
    data class Attributes(
        val email: String,

        @SerialName("is_email_verified")
        val isEmailVerified: Boolean,

        @SerialName("first_name")
        val firstName: String,
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
@SerialName("member")
class PatreonMember(
    override val id: String,
) : PatreonEntity()