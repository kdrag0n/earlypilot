package dev.kdrag0n.patreondl.content.filters

import dev.kdrag0n.patreondl.config.Config
import io.ktor.server.application.*
import java.io.InputStream
import java.io.OutputStream

interface ContentFilter {
    // Constructor (via reflection): (environment: ApplicationEnvironment, config: Config)

    /** Copy data from [ins] to [os] until EOF and filter it. )*/
    fun writeData(call: ApplicationCall, ins: InputStream, os: OutputStream)

    /** Get the length of the final, filtered payload given the size of original in bytes. */
    fun getFinalLength(call: ApplicationCall, len: Long): Long

    companion object {
        fun createByName(environment: ApplicationEnvironment, config: Config, className: String): ContentFilter {
            return Class.forName(className)
                .getDeclaredConstructor(ApplicationEnvironment::class.java, Config::class.java)
                .newInstance(environment, config) as ContentFilter
        }
    }
}
