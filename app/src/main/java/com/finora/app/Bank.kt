package com.finora.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import org.json.JSONObject

val bankApps = linkedMapOf("State Bank of India" to "com.sbi.lotusintouch","Bank of Maharashtra" to "",
    "Maharashtra Gramin Bank" to "", "Bank of India" to "", "HDFC Bank" to "com.snapwork.hdfc",
    "ICICI Bank" to "com.csam.icici.bank.imobile", "Axis Bank" to "com.axis.mobile","Canara Bank" to "",
    "Punjab National Bank" to "", "Union Bank of India" to "", "Kotak Mahindra Bank" to "", "Indian Bank" to "")
object BankParser {
    private val secret = Regex("""(?i)otp|one.time|password|passcode|\bpin\b|verification|authenticat|login|declined|failed|pending|request|revers""")
    private val amount = Regex("""(?i)(?:INR|Rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?![\d.])""")
    fun parse(text:String):Pair<Long,Boolean>? {
        if (secret.containsMatchIn(text)) return null
        val debit = Regex("""(?i)\bdebited\b""").containsMatchIn(text)
        val credit = Regex("""(?i)\bcredited\b""").containsMatchIn(text)
        if (debit == credit) return null
        val matches = amount.findAll(text).toList()
        val value = matches.singleOrNull()?.groupValues?.get(1)?.replace(",","")?.let(::paise) ?: return null
        return if(value>0) value to credit else null
    }
}
fun Context.inbox(uid:String) = getSharedPreferences("review-"+uid,Context.MODE_PRIVATE)
class BankListener:NotificationListenerService() {
    override fun onNotificationPosted(sbn:StatusBarNotification) {
        val bank = bankApps.entries.firstOrNull { it.value == sbn.packageName }?.key ?: return
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
        val store = inbox(uid)
        if(!store.getBoolean("enabled",false) || bank !in store.getStringSet("selected",emptySet()).orEmpty()) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: return
        val (value,credit) = BankParser.parse("$title $body") ?: return
        val identity = listOf(uid,sbn.packageName,sbn.key,sbn.postTime,value,credit).joinToString("|")
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        store.edit().putString("tx-"+key,JSONObject().put("bank",bank).put("amount",value).put("credit",credit).put("time",sbn.postTime).toString()).apply()
    }
}
