package dev.kdrag0n.patreondl

import com.patreon.PatreonAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PatreonApi {
    suspend fun getIdentity(token: String): com.patreon.resources.User {
        return withContext(Dispatchers.IO) {
            // False-positive caused by IOException
            @Suppress("BlockingMethodInNonBlockingContext")
            PatreonAPI(token).fetchUser().get()
                ?: error("Failed to fetch Patreon user info")
        }
    }
}