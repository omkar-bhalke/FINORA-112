package com.finora.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import kotlinx.coroutines.*

val bankApps =
    linkedMapOf(
        "State Bank of India" to "com.sbi.lotusintouch",
        "Bank of Maharashtra" to "com.bom.lifestylebanking",
        "Maharashtra Gramin Bank" to "com.mbanking.mgb.mgb",
        "Bank of India" to "com.boi.ua.android",
        "HDFC Bank" to "com.hdfcbank.android.now",
        "ICICI Bank" to "com.csam.icici.bank.imobile",
        "Axis Bank" to "com.axis.mobile",
        "Canara Bank" to "com.canarabank.mobility",
        "Punjab National Bank" to "com.Version1",
        "Union Bank of India" to "com.infrasoft.uboi",
        "Kotak Mahindra Bank" to "com.kotak.bank.mobile",
        "Indian Bank" to "com.iexceed.ib.digitalbankingprod",
    )

object BankParser {
    private val secret =
        Regex(
            """(?i)otp|one.time|password|passcode|\bpin\b|verification|authenticat|login|declined|failed|pending|request|revers"""
        )
    private val amount = Regex("""(?i)(?:INR|Rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?![\d.])""")

    fun parse(text: String): Pair<Long, Boolean>? {
        if (secret.containsMatchIn(text)) return null
        val debit = Regex("""(?i)\bdebited\b""").containsMatchIn(text)
        val credit = Regex("""(?i)\bcredited\b""").containsMatchIn(text)
        if (debit == credit) return null
        val matches = amount.findAll(text).toList()
        val value =
            matches.singleOrNull()?.groupValues?.get(1)?.replace(",", "")?.let(::paise)
                ?: return null
        return if (value > 0) value to credit else null
    }
}

fun Context.inbox(uid: String) = getSharedPreferences("review-" + uid, Context.MODE_PRIVATE)

class BankListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val bank = bankApps.entries.firstOrNull { it.value == sbn.packageName }?.key ?: return
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
        val store = inbox(uid)
        if (
            !store.getBoolean("enabled", false) ||
                bank !in store.getStringSet("selected", emptySet()).orEmpty()
        )
            return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body =
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: return
        val match = parserProfiles[bank]?.recognize("$title $body") ?: return
        val value = match.amount
        val credit = match.credit
        val identity =
            if (match.reference.isNotBlank())
                listOf(uid, sbn.packageName, match.masked, match.reference, value, credit)
                    .joinToString("|")
            else
                listOf(uid, sbn.packageName, sbn.key, sbn.postTime, value, credit).joinToString("|")
        val key =
            MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        scope.launch {
            LocalLedger.get(applicationContext)
                .reviews()
                .add(
                    Candidate(
                        uid,
                        "tx-" + key,
                        bank,
                        value,
                        credit,
                        sbn.postTime,
                        match.masked,
                        match.reference,
                        match.confidence,
                    )
                )
        }
    }
}
