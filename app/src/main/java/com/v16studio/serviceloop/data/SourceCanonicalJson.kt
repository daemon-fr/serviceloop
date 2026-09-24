package com.v16studio.serviceloop.data

import java.math.BigDecimal
import java.math.BigInteger
import org.json.JSONArray
import org.json.JSONObject

/** Stable UTF-8 source-fact encoding for v2 immutable comparisons. */
internal object SourceCanonicalJson {
    fun bytes(value: Any?): ByteArray = text(value).toByteArray(Charsets.UTF_8)

    fun text(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sortedWith(::compareCodePoints)
            .joinToString(",", "{", "}") { key -> "${quote(key)}:${text(value.get(key))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { text(value.get(it)) }
        is String -> quote(value)
        is Boolean -> if (value) "true" else "false"
        is Byte, is Short, is Int, is Long, is BigInteger -> value.toString()
        is BigDecimal -> value.toPlainString()
        else -> throw IllegalArgumentException("Unsupported source JSON value: ${value.javaClass.simpleName}")
    }

    private fun compareCodePoints(left: String, right: String): Int {
        var a = 0
        var b = 0
        while (a < left.length && b < right.length) {
            val x = left.codePointAt(a)
            val y = right.codePointAt(b)
            if (x != y) return x.compareTo(y)
            a += Character.charCount(x)
            b += Character.charCount(y)
        }
        return (left.length - a).compareTo(right.length - b)
    }

    private fun quote(value: String): String = buildString {
        append('"')
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> when {
                    char.code < 0x20 -> append("\\u${char.code.toString(16).padStart(4, '0')}")
                    Character.isHighSurrogate(char) -> {
                        require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) { "Unpaired high surrogate" }
                        append(char)
                        append(value[++index])
                    }
                    Character.isLowSurrogate(char) -> throw IllegalArgumentException("Unpaired low surrogate")
                    else -> append(char)
                }
            }
            index++
        }
        append('"')
    }
}
