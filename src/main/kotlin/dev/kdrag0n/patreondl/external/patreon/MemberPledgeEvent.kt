package dev.kdrag0n.patreondl.external.patreon

import kotlinx.serialization.Serializable

@Serializable
data class MemberPledgeEvent(
    val data: PatreonMember,
    val included: List<PatreonEntity>
)