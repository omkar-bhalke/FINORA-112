package com.finora.app
import org.junit.Assert.*
import org.junit.Test
class LedgerTest {
    @Test fun amounts(){assertEquals(10001L,paise("100.01"));assertNull(paise("0.009"));assertNull(paise("-10"))}
    @Test fun parser(){assertEquals(12550L to false,BankParser.parse("INR 125.50 debited"));assertNull(BankParser.parse("OTP for INR 12 debited"));assertNull(BankParser.parse("Rs 12 debited. Balance INR 1,500"));assertNull(BankParser.parse("INR 1 credited and debited"))}
    @Test fun totals(){assertEquals(250L,listOf(Entry(amount=250),Entry(amount=900,credit=true)).spent())}
}
