package dev.kdrag0n.patreondl

import com.google.common.cache.CacheBuilder
import com.patreon.PatreonAPI
import com.patreon.resources.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class PatreonApi {
    // Token -> User mapping
    private val identityCache = CacheBuilder.newBuilder().run {
        maximumSize(500)
        // Important: benefits need to be invalidated in a timely manner
        expireAfterWrite(Duration.ofHours(12))
        // We can't use a LoadingCache here because of coroutines
        build<String, User>()
    }

    suspend fun getIdentity(token: String): User {
        return identityCache.getIfPresent(token) ?: withContext(Dispatchers.IO) {
            // False-positive caused by IOException
            @Suppress("BlockingMethodInNonBlockingContext")
            val user = PatreonAPI(token).fetchUser().get()
                ?: error("Failed to fetch Patreon user info")
            identityCache.put(token, user)
            return@withContext user
        }
    }
}