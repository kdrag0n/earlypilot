package dev.kdrag0n.patreondl.filters

import io.ktor.application.*
import java.io.InputStream
import java.io.OutputStream

interface ContentFilter {
    fun writeData(ins: InputStream, os: OutputStream, environment: ApplicationEnvironment, call: ApplicationCall) {
        ins.copyTo(os)
    }
}