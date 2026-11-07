package com.minagawer.spinback.data

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.Normalizer

/**
 * Orders names the way a Japanese iPod does: a leading "The" is ignored, case and katakana vs
 * hiragana don't matter, and the rest goes by Shift_JIS code order. That puts Latin first, then
 * kana in syllabary order, then kanji in JIS order (not by reading). Leading symbols such as
 * "#", "(" or quotes are skipped, and names that then start with a digit come after the rest,
 * with names made only of symbols at the very end; digit runs compare by value. Accents are
 * dropped (é sorts as e), and characters Shift_JIS can't hold sort after everything that it can.
 */
object IpodOrder : Comparator<String> {
    private val LATIN_ACCENTS = Regex("[\u0300-\u036F]")

    // Windows' Shift_JIS, which also has characters like ① that plain Shift_JIS lacks.
    private val shiftJis = listOf("windows-31j", "MS932", "Shift_JIS").first(Charset::isSupported)
        .let(Charset::forName)
    private val cache = HashMap<String, ByteArray>()

    /** Letters that don't come apart into a base letter and an accent. */
    private val foldedLetters = mapOf(
        'Ø' to "O", 'ø' to "O", 'Æ' to "AE", 'æ' to "AE", 'Œ' to "OE", 'œ' to "OE",
        'ß' to "SS", 'Đ' to "D", 'đ' to "D", 'Ł' to "L", 'ł' to "L", 'Þ' to "TH", 'þ' to "TH",
    )

    override fun compare(a: String, b: String): Int {
        val group = group(a).compareTo(group(b))
        if (group != 0) return group
        val x = key(a)
        val y = key(b)
        var i = 0
        var j = 0
        while (i < x.size && j < y.size) {
            // Runs of digits compare as numbers, so 4 < 13 < 1830. (Shift_JIS never uses ASCII
            // digits inside a double-byte character, so a digit byte is always a real digit.)
            if (x[i].isDigit() && y[j].isDigit()) {
                val xEnd = digitsEnd(x, i)
                val yEnd = digitsEnd(y, j)
                val d = compareNumbers(x, i, xEnd, y, j, yEnd)
                if (d != 0) return d
                i = xEnd
                j = yEnd
                continue
            }
            val d = (x[i].toInt() and 0xFF) - (y[j].toInt() and 0xFF)
            if (d != 0) return d
            i++
            j++
        }
        return (x.size - i) - (y.size - j)
    }

    private fun Byte.isDigit() = toInt() in '0'.code..'9'.code

    private fun digitsEnd(b: ByteArray, from: Int): Int {
        var end = from
        while (end < b.size && b[end].isDigit()) end++
        return end
    }

    /** Compares two digit runs by value, however long they are. */
    private fun compareNumbers(
        x: ByteArray,
        xs: Int,
        xe: Int,
        y: ByteArray,
        ys: Int,
        ye: Int
    ): Int {
        var xi = xs
        var yi = ys
        while (xi < xe - 1 && x[xi].toInt() == '0'.code) xi++
        while (yi < ye - 1 && y[yi].toInt() == '0'.code) yi++
        val lengths = (xe - xi) - (ye - yi)
        if (lengths != 0) return lengths
        while (xi < xe) {
            val d = x[xi] - y[yi]
            if (d != 0) return d
            xi++
            yi++
        }
        return 0
    }

    /** 0 for names starting with a letter, 1 with a digit, 2 for names that are only symbols. */
    private fun group(s: String): Int {
        val first = key(s).firstOrNull() ?: return 2
        return if (first.isDigit()) 1 else 0
    }

    private fun key(s: String): ByteArray = synchronized(cache) {
        cache.getOrPut(s) { encode(normalize(s)) }
    }

    /**
     * Shift_JIS bytes, except that a character it can't hold becomes 0xFF and its code point:
     * above any Shift_JIS byte, so it sorts last, yet still distinct from other such characters.
     */
    private fun encode(s: String): ByteArray {
        val encoder = shiftJis.newEncoder()
        val out = ByteArrayOutputStream(s.length * 2)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            if (encoder.canEncode(ch)) {
                out.write(ch.toByteArray(shiftJis))
            } else {
                out.write(0xFF)
                out.write(cp shr 16)
                out.write(cp shr 8)
                out.write(cp)
            }
            i += Character.charCount(cp)
        }
        return out.toByteArray()
    }

    private fun normalize(s: String): String {
        // Compatibility forms first: full-width to ASCII, half-width kana to full, ① to 1. Then drop
        // Latin accents, but put kana voicing marks back (が must stay が, not become か).
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFKD)
            .replace(LATIN_ACCENTS, "")
            .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
            .map { foldedLetters[it] ?: it.toString() }
            .joinToString("")
        val trimmed = decomposed.trimStart { !it.isLetterOrDigit() }
            .let { if (it.startsWith("the ", ignoreCase = true)) it.drop(4) else it }
            .trimStart { !it.isLetterOrDigit() }
        return buildString {
            for (c in trimmed) {
                append(
                    when (c) {
                        in 'ァ'..'ヶ' -> c - ('ァ' - 'ぁ')
                        else -> c
                    }.uppercaseChar()
                )
            }
        }
    }
}
