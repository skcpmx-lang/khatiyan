package com.shohan.khatiyan.utilities

import org.junit.Assert.assertEquals
import org.junit.Test

class BnTextTest {

    @Test
    fun digitConversionsRoundTrip() {
        assertEquals("১২৩৪", BnText.toBnDigits("1234"))
        assertEquals("1234", BnText.fromBnDigits("১২৩৪"))
        assertEquals("৳১০.৫০", BnText.toBnDigits("৳10.50"))
        assertEquals("abc", BnText.toBnDigits("abc"))
    }

    @Test
    fun groupInteger_westernThousands() {
        assertEquals("7", BnText.groupInteger("7"))
        assertEquals("123", BnText.groupInteger("123"))
        assertEquals("1,234", BnText.groupInteger("1234"))
        assertEquals("1,234,567", BnText.groupInteger("1234567"))
        assertEquals("12,345,678", BnText.groupInteger("12345678"))
    }

    @Test
    fun ellipsize_neverLongerThanMax() {
        assertEquals("কাছ", BnText.ellipsize("কাছ", 5))
        val cut = BnText.ellipsize("১২৩৪৫৬৭৮৯", 5)
        assertEquals(5, cut.length)
        assertEquals("১২৩৪…", cut)
    }
}
