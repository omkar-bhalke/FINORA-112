package com.finora.app

data class BankMatch(
    val amount: Long,
    val credit: Boolean,
    val masked: String,
    val reference: String,
    val confidence: Double,
)

interface BankNotificationParser {
    fun recognize(content: String): BankMatch?
}

class NotificationProfile(private val referencePattern: Regex) : BankNotificationParser {
    override fun recognize(content: String): BankMatch? {
        val (amount, credit) = BankParser.parse(content) ?: return null
        val account = mask.find(content)?.groupValues?.get(1).orEmpty()
        val reference = referencePattern.find(content)?.groupValues?.get(1).orEmpty()
        return BankMatch(
            amount,
            credit,
            account,
            reference,
            if (reference.isNotEmpty() && account.isNotEmpty()) .85 else .60,
        )
    }

    companion object {
        private val mask =
            Regex("""(?i)(?:a/c|acct|account)\s*(?:no\.?\s*)?(?:ending\s+|[x*•]+)(\d{4})\b""")
    }
}

private val ref =
    Regex("""(?i)\b(?:ref(?:erence)?(?:\s*no\.?)?|utr|rrn)\s*[:#-]?\s*([A-Z0-9]{6,24})\b""")
private val upiRef =
    Regex(
        """(?i)\b(?:upi\s*ref(?:\s*no\.?)?|utr|rrn|ref(?:\s*no\.?)?)\s*[:#-]?\s*([A-Z0-9]{6,24})\b"""
    )
val parserProfiles: Map<String, BankNotificationParser> =
    bankApps.keys.associateWith {
        when (it) {
            "State Bank of India",
            "HDFC Bank",
            "ICICI Bank",
            "Axis Bank" -> NotificationProfile(upiRef)
            else -> NotificationProfile(ref)
        }
    }
