package dev.kdrag0n.patreondl.content.filters

import dev.kdrag0n.patreondl.config.Config
import io.ktor.server.application.*
import java.io.InputStream
import java.io.OutputStream

class PassthroughFilter(
    environment: ApplicationEnvironment,
    config: Config,
) : ContentFilter {
    override fun writeData(call: ApplicationCall, ins: InputStream, os: OutputStream) {
        ins.copyTo(os)
    }

    override fun getFinalLength(call: ApplicationCall, len: Long): Long {
        return len
    }
}
