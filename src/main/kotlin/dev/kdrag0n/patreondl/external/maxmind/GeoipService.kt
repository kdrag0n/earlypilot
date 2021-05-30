package dev.kdrag0n.patreondl.external.maxmind

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import dev.kdrag0n.patreondl.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.util.*

class GeoipService(
    config: Config,
) {
    private val dbReader = DatabaseReader.Builder(File(config.external.maxmind.databasePath)).run {
        withCache(CHMCache())
        build()
    }

    suspend fun getCountry(textIp: String): String {
        // On I/O thread
        @Suppress("BlockingMethodInNonBlockingContext")
        val country = withContext(Dispatchers.IO) {
            val ip = InetAddress.getByName(textIp)
            dbReader.country(ip).country
        }

        // Convert to ISO alpha-3 for PriceManager
        return Locale("en", country.isoCode).isO3Country
    }
}