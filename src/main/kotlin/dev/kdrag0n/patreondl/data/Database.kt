package dev.kdrag0n.patreondl.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.kdrag0n.patreondl.config.Config
import io.ktor.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject

fun Application.initDatabase() {
    val config: Config by inject()
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${config.database.host}:${config.database.port}/${config.database.database}"
        username = config.database.username
        password = config.database.password
    }

    Database.connect(HikariDataSource(hikariConfig))

    transaction {
        SchemaUtils.create(
            Users,
            DownloadEvents,
            Grants,
            Products,
            Purchases,
        )
    }
}
