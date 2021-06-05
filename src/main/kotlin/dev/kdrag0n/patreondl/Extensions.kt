package dev.kdrag0n.patreondl

private val WHITESPACE_REGEX = Regex("""\s+""")

fun String.splitWhitespace(): List<String> {
    return split(WHITESPACE_REGEX)
}
