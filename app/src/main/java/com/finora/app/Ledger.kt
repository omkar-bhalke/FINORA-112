package com.finora.app

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.util.Locale

val categories =
    listOf("Food", "Travel", "Shopping", "Bills", "Entertainment", "Education", "Health", "Others")

fun paise(s: String): Long? =
    s.trim()
        .takeIf { it.matches(Regex("""\d{1,10}(\.\d{1,2})?""")) }
        ?.let { BigDecimal(it).movePointRight(2).longValueExact() }

fun money(v: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(BigDecimal.valueOf(v, 2))

data class Entry(
    val id: String = "",
    val title: String = "",
    val amount: Long = 0,
    val credit: Boolean = false,
    val category: String = "Others",
    val time: Long = 0,
    val source: String = "Manual",
    val bank: String = "",
    val fixed: Boolean = false,
    val notes: String = "",
    val method: String = "",
    val reference: String = "",
    val account: String = "",
    val confidence: Double = 1.0,
    val reconciliation: String = "User entered",
)

fun Entry.date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

fun List<Entry>.spent() = filterNot { it.credit }.sumOf { it.amount }

data class Profile(
    val name: String = "",
    val ready: Boolean = false,
    val banks: List<String> = emptyList(),
    val daily: Long = 0,
    val monthly: Long = 0,
    val opening: Long? = null,
    val theme: String = "Emerald Finance",
    val mode: String = "System",
    val phone: String = "",
    val country: String = "India",
    val control: String = "Off",
    val payments: List<String> = emptyList(),
)

data class Plan(
    val id: String = "",
    val title: String = "",
    val amount: Long = 0,
    val due: String = "",
    val kind: String = "",
    val progress: Long = 0,
    val clock: String = "09:00",
    val frequency: String = "Once",
    val enabled: Boolean = true,
)
