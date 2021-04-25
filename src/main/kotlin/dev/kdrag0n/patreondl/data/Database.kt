package dev.kdrag0n.patreondl.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.databaseModule(): Boolean {
    // Bail out if DB is disabled
    if (environment.config.propertyOrNull("database.enabled")?.getString()?.toBoolean() != true) {
        return false
    }

    val config = HikariConfig().apply {
        val cfgHost = environment.config.property("database.host").getString()
        val cfgPort = environment.config.property("database.port").getString()
        val cfgDatabase = environment.config.property("database.database").getString()
        val cfgUsername = environment.config.property("database.username").getString()
        val cfgPassword = environment.config.propertyOrNull("database.password")?.getString() // optional

        jdbcUrl = "jdbc:postgresql://$cfgHost:$cfgPort/$cfgDatabase"
        username = cfgUsername
        password = cfgPassword
    }

    Database.connect(HikariDataSource(config))

    transaction {
        SchemaUtils.create(Users, DownloadEvents)
    }

    return true
}
