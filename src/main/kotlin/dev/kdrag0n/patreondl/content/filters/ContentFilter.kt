package dev.kdrag0n.patreondl.content.filters

import dev.kdrag0n.patreondl.config.Config
import io.ktor.application.*
import java.io.InputStream
import java.io.OutputStream

interface ContentFilter {
    // Constructor: (environment: ApplicationEnvironment, config: Config)

    fun writeData(call: ApplicationCall, ins: InputStream, os: OutputStream)

    fun getFinalLength(call: ApplicationCall, len: Long): Long

    companion object {
        fun createByName(environment: ApplicationEnvironment, config: Config, className: String): ContentFilter {
            return Class.forName(className)
                .getDeclaredConstructor(ApplicationEnvironment::class.java, Config::class.java)
                .newInstance(environment, config) as ContentFilter
        }
    }
}