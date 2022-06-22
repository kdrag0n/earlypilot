package dev.kdrag0n.patreondl.external.patreon

import com.google.common.cache.CacheBuilder
import com.patreon.PatreonAPI
import com.patreon.resources.RequestUtil
import com.patreon.resources.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Duration

// Needed to inject PatreonRequestWrapper
private val apiCtor = PatreonAPI::class.java
    .getDeclaredConstructor(String::class.java, RequestUtil::class.java)
    .apply { isAccessible = true }

class PatreonApi {
    // Token -> User mapping
    private val identityCache = CacheBuilder.newBuilder().run {
        maximumSize(200)
        // Important: benefits need to be invalidated in a timely manner
        // We also need to give users a chance to fix their pledges
        expireAfterWrite(Duration.ofHours(2))
        // We can't use a LoadingCache here because of coroutines
        build<String, User>()
    }

    fun invalidateUser(id: String) {
        identityCache.asMap().forEach { (token, user) ->
            if (user.id == id) {
                identityCache.invalidate(token)
            }
        }
    }

    suspend fun getIdentity(token: String): User {
        return identityCache.getIfPresent(token) ?: withContext(Dispatchers.IO) {
            val api = apiCtor.newInstance(token, PatreonRequestWrapper)
            val user = api.fetchUser().get()
                ?: error("Failed to fetch Patreon user info")
            identityCache.put(token, user)
            return@withContext user
        }
    }
}

private object PatreonRequestWrapper : RequestUtil() {
    override fun request(pathSuffix: String, accessToken: String): InputStream {
        /*
         * Newer versions of Apache httpcomponents result in pathSuffix being "/current_user" with
         * Patreon's usage. The path is concatenated to the base API URL, resulting in a double slash:
         * https://www.patreon.com/api/oauth2/api//current_user?include=pledges
         *
         * Patreon API responds with a 308 redirect + HTML page to remove the double slash. Java HTTP
         * client doesn't follow redirects, so we need to fix the URL manually.
         */
        return super.request(pathSuffix.trimStart('/'), accessToken)
    }
}
