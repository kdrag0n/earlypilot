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
    private val conversionRates = readLatestRates(PPP_RATES_PATH)

    init {
        if (DEBUG_PPP_RATES) {
            printRates()
        }
    }

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

    private fun readLatestRates(csvPath: String): Map<String, Double> {
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(csvPath)
            ?: error("Rates not found: $csvPath")

        return csvReader().open(inputStream) {
            // Skip data source and last update date
            repeat(4) { readNext() }

            readAllWithHeaderAsSequence()
                .filter { row -> row["Indicator Code"] == "PA.NUS.PPPC.RF" }
                .flatMap { row ->
                    row.entries.asSequence()
                        .filter { it.key.toIntOrNull() != null && it.value.isNotEmpty() }
                        .map {
                            RateItem(
                                country = row["Country Code"]!!,
                                year = it.key.toInt(),
                                rate = it.value.toDouble(),
                            )
                        }
                }
                .groupBy { it.country }
                .mapValues { entry -> entry.value.maxByOrNull { it.year }!!.rate }
                .toMap()
        }
    }

    private fun printRates() = conversionRates.forEach { (country, rate) ->
        logger.debug("$country -> $rate")
    }

    companion object {
        private const val PPP_RATES_PATH = "data/wdi_ppp_rates.csv"
        private const val DEBUG_PPP_RATES = false

        private val logger = LoggerFactory.getLogger(PriceManager::class.java)
    }
}

private data class RateItem(
    val country: String,
    val year: Int,
    val rate: Double,
)
