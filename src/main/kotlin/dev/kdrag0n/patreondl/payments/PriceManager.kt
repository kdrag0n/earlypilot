package dev.kdrag0n.patreondl.payments

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import dev.kdrag0n.patreondl.config.Config
import dev.kdrag0n.patreondl.data.Product
import dev.kdrag0n.patreondl.external.maxmind.GeoipService
import org.slf4j.LoggerFactory
import java.text.NumberFormat

class PriceManager(
    private val config: Config,
    private val geoipService: GeoipService,
    private val currencyFormat: NumberFormat,
) {
    private val minPrice = config.payments.minPriceCents.toDouble() / 100
    private val conversionRates = calcConversionFactors(
        ppp = readLatestRates(PPP_RATES_PATH),
        exchange = readLatestRates(EXCHANGE_RATES_PATH),
    )

    suspend fun getPriceForProduct(product: Product, clientIp: String): Int {
        val country = try {
            geoipService.getCountry(clientIp)
        } catch (e: Exception) {
            logger.error("Failed to get country for client IP $clientIp", e)
            // Fallback = original USD price
            "USA"
        }

        val productPrice = (product.priceCents
            ?: config.payments.defaultPriceCents).toDouble() / 100
        val conversionFactor = conversionRates[country] ?: 1.0

        val newPrice = (productPrice * conversionFactor).coerceAtLeast(minPrice)
        logger.info("Country $country => price ${currencyFormat.format(newPrice)}")
        return (newPrice * 100).toInt()
    }

    private fun calcConversionFactors(
        ppp: Map<String, Double>,
        exchange: Map<String, Double>,
    ): Map<String, Double> {
        return ppp.mapNotNull {
            // Country not found = 1.0
            val exchangeRate = exchange[it.key] ?: it.value
            it.key to (it.value / exchangeRate)
        }.toMap()
    }

    private fun readLatestRates(csvPath: String): Map<String, Double> {
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(csvPath)
            ?: error("Rates not found: $csvPath")

        return csvReader().open(inputStream) {
            readAllWithHeaderAsSequence()
                .map { row ->
                    RateItem(
                        country = row["LOCATION"]!!,
                        year = row["TIME"]!!.toInt(),
                        rate = row["Value"]!!.toDouble(),
                    )
                }
                .groupBy { it.country }
                .mapValues { entry -> entry.value.maxByOrNull { it.year }!!.rate }
                .toMap()
        }
    }

    companion object {
        private const val PPP_RATES_PATH = "data/oecd_ppp_rates.csv"
        private const val EXCHANGE_RATES_PATH = "data/oecd_exchange_rates.csv"

        private val logger = LoggerFactory.getLogger(PriceManager::class.java)
    }
}

private data class RateItem(
    val country: String,
    val year: Int,
    val rate: Double,
)