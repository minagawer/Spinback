package com.minagawer.spinback.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The ordering rules of a Japanese iPod classic, with made-up names. */
class IpodOrderTest {
    private fun sorted(vararg names: String) = names.sortedWith(IpodOrder)

    @Test
    fun latinThenKanaThenKanjiThenDigits() {
        val expected =
            listOf("Alpha", "beta", "Zulu", "あさひ", "カメラ", "わかば", "山", "1st", "2nd")
        assertEquals(expected, expected.shuffled(kotlin.random.Random(1)).sortedWith(IpodOrder))
    }

    @Test
    fun katakanaAndHiraganaSortTogether() {
        assertEquals(listOf("クリーム", "くるま", "サクラ"), sorted("サクラ", "くるま", "クリーム"))
    }

    @Test
    fun kanjiFollowJisOrderNotReading() {
        // 山 (サン) comes before 川 (セン) in JIS, though かわ reads before やま.
        assertEquals(listOf("山", "川"), sorted("川", "山"))
        // Second-level JIS kanji come after first-level ones.
        assertEquals(listOf("川", "坩"), sorted("坩", "川"))
    }

    @Test
    fun namesStartingWithDigitsComeLastByValue() {
        assertEquals(
            listOf("山", "1", "1.5", "3×3", "#4", "13/7", "14 a", "20th", "1830m", "1000000000000"),
            sorted("1000000000000", "20th", "#4", "1830m", "山", "14 a", "1", "13/7", "3×3", "1.5"),
        )
    }

    @Test
    fun namesMadeOnlyOfSymbolsComeLast() {
        assertEquals(
            listOf("Alpha", "山", "1st", "・・・。"),
            sorted("・・・。", "1st", "山", "Alpha")
        )
    }

    @Test
    fun digitsInsideNamesCompareByValue() {
        assertEquals(listOf("Disc 2", "Disc 10"), sorted("Disc 10", "Disc 2"))
    }

    @Test
    fun leadingSymbolsAndTheAreSkipped() {
        assertEquals(
            listOf("Fox", "“Golf” Hotel", "Hotel", "Whale", "(Whisky) Echo", "Zulu"),
            sorted("Zulu", "(Whisky) Echo", "Hotel", "“Golf” Hotel", "Whale", "Fox"),
        )
        assertEquals(listOf("Beach", "The Bear", "Bee"), sorted("Bee", "The Bear", "Beach"))
    }

    @Test
    fun accentsAndWidthDoNotMatter() {
        assertEquals(listOf("Cafe", "Café latte", "Cafes"), sorted("Cafes", "Café latte", "Cafe"))
        assertEquals(listOf("Moo", "Möp", "Moz"), sorted("Moz", "Möp", "Moo"))
        assertEquals(listOf("ＡＢＣ", "ABD"), sorted("ABD", "ＡＢＣ"))
        assertEquals(listOf("BOOK", "BOØT", "BOX"), sorted("BOX", "BOØT", "BOOK"))
    }

    @Test
    fun voicedKanaStayDistinct() {
        assertEquals(listOf("かき", "がき", "きく"), sorted("きく", "がき", "かき"))
    }

    @Test
    fun halfWidthKatakanaSortsWithKana() {
        assertEquals(listOf("あ", "ｶﾞｷ", "さ"), sorted("さ", "ｶﾞｷ", "あ"))
    }

    @Test
    fun charactersShiftJisLacksComeAfterKanjiButBeforeDigits() {
        assertEquals(listOf("山", "한국", "1"), sorted("1", "한국", "山"))
    }
}
