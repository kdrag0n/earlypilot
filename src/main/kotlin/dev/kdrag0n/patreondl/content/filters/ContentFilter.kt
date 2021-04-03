package dev.kdrag0n.patreondl.content.filters

import io.ktor.application.*
import java.io.InputStream
import java.io.OutputStream

interface ContentFilter {
    fun writeData(ins: InputStream, os: OutputStream, environment: ApplicationEnvironment, call: ApplicationCall) {
        ins.copyTo(os)
    }
}