package dev.kdrag0n.patreondl.content.filters

import io.ktor.application.*
import java.io.InputStream
import java.io.OutputStream

interface ContentFilter {
    fun writeData(environment: ApplicationEnvironment, call: ApplicationCall, ins: InputStream, os: OutputStream) {
        ins.copyTo(os)
    }

    fun getFinalLength(environment: ApplicationEnvironment, call: ApplicationCall, len: Long): Long {
        return len
    }
}