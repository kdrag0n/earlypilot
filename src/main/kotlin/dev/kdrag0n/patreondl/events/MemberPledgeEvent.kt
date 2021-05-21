package dev.kdrag0n.patreondl.events

import kotlinx.serialization.Serializable

@Serializable
data class MemberPledgeEvent(
    val data: Data,
) {
    @Serializable
    data class Data(
        val relationships: Relationships,
    )

    @Serializable
    data class Relationships(
        val user: UserReference,
    )
}

@Serializable
data class UserReference(
    val data: Data,
) {
    @Serializable
    data class Data(
        val id: String,
    )
}