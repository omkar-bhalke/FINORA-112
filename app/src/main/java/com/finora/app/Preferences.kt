package com.finora.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.tasks.await

@Composable
fun Banks(p: Profile, m: LedgerModel, setup: Boolean, done: () -> Unit) {
    var selected by remember { mutableStateOf(p.banks) }
    var query by rememberSaveable { mutableStateOf("") }
    Heading("Select Your Bank")
    Text(
        "Choose your bank to configure FINORA's supported transaction tracking and financial notification features."
    )
    Field("Search banks", query) { query = it }
    bankApps.keys
        .filter { it.contains(query, true) }
        .forEach { bank ->
            Panel {
                Row {
                    Checkbox(
                        bank in selected,
                        { selected = if (it) selected + bank else selected - bank },
                    )
                    Text(bank)
                }
                Text(
                    if (bankApps[bank].isNullOrEmpty()) "Manual tracking"
                    else "Notification review available"
                )
                if (bank in selected) {
                    TextButton({ selected = listOf(bank) + (selected - bank) }) {
                        Text(if (selected.first() == bank) "Primary bank" else "Make primary")
                    }
                }
            }
        }
    Button({ m.write(done) { m.update(mapOf("banks" to selected, "ready" to true)) } }) {
        Text(if (setup && selected.isEmpty()) "Skip for now" else "Save selection")
    }
    Text("Bank balance synchronization requires authorized bank integration.")
}

@Composable
fun Access(m: LedgerModel, p: Profile) {
    val ctx = LocalContext.current
    val store = remember(m.uid) { ctx.inbox(m.uid) }
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, store) {
        val watch =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                revision++
            }
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        store.registerOnSharedPreferenceChangeListener(watch)
        owner.lifecycle.addObserver(resume)
        onDispose {
            store.unregisterOnSharedPreferenceChangeListener(watch)
            owner.lifecycle.removeObserver(resume)
        }
    }
    val snapshot = remember(revision) { store.all }
    val granted =
        remember(revision) {
            ctx.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(ctx, BankListener::class.java))
        }
    Heading("Bank Notification Access")
    Text(
        "FINORA can identify eligible banking transaction notifications. Selected bank apps are checked locally. Review each candidate before saving it."
    )
    Text(if (granted) "Android access enabled" else "Android access disabled")
    OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
        Text(if (granted) "Manage Android access" else "Enable Bank Notification Access")
    }
    Row {
        Checkbox(snapshot["enabled"] == true, { store.edit().putBoolean("enabled", it).apply() })
        Text("Process selected banking apps")
    }
    Heading("Banking apps detected")
    p.banks.forEach { bank ->
        val pkg = bankApps[bank].orEmpty()
        val status =
            if (pkg.isEmpty()) "Package unverified"
            else if (runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess)
                "Installed"
            else "Not detected"
        Text("$bank · $status")
    }
    Heading("Review queue")
    val dao = remember(ctx) { LocalLedger.get(ctx).reviews() }
    val pending by remember(m.uid) { dao.observe(m.uid) }.collectAsState(initial = emptyList())
    if (pending.isEmpty()) Text("No eligible notifications awaiting review.")
    pending.forEach { item ->
        key(item.id) {
            Panel {
                Text(item.bank)
                if (item.masked.isNotEmpty()) Text("Account •••• " + item.masked)
                if (item.reference.isNotEmpty()) Text("Reference " + item.reference)
                Text("Parser confidence · " + (item.confidence * 100).toInt() + "%")
                Text((if (item.credit) "Credit · " else "Debit · ") + money(item.amount))
                Text(Instant.ofEpochMilli(item.time).atZone(ZoneId.systemDefault()).toString())
                var cat by remember { mutableStateOf("Others") }
                Choice("Category", cat, categories) { cat = it }
                Button({
                    m.write {
                        m.save(
                            Entry(
                                id = item.id,
                                title = item.bank + " transaction",
                                amount = item.amount,
                                credit = item.credit,
                                category = cat,
                                time = item.time,
                                source = "Bank Notification",
                                bank = item.bank,
                                reference = item.reference,
                                account =
                                    m.accounts.value
                                        .singleOrNull {
                                            it.bank == item.bank && it.lastFour == item.masked
                                        }
                                        ?.id
                                        .orEmpty(),
                                confidence = item.confidence,
                                reconciliation = "User confirmed",
                            )
                        )
                        dao.remove(m.uid, item.id)
                    }
                }) {
                    Text("Confirm & import")
                }
                TextButton({ m.write { dao.remove(m.uid, item.id) } }) { Text("Discard") }
            }
        }
    }
}

@Composable
fun Settings(p: Profile, m: LedgerModel, done: () -> Unit) {
    fun decimal(v: Long?) = v?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()
    var name by rememberSaveable { mutableStateOf(p.name) }
    var phone by rememberSaveable { mutableStateOf(p.phone) }
    var country by rememberSaveable { mutableStateOf(p.country) }
    var daily by rememberSaveable { mutableStateOf(decimal(p.daily)) }
    var monthly by rememberSaveable { mutableStateOf(decimal(p.monthly)) }
    var opening by rememberSaveable { mutableStateOf(decimal(p.opening)) }
    var theme by rememberSaveable { mutableStateOf(p.theme) }
    var mode by rememberSaveable { mutableStateOf(p.mode) }
    Heading("Profile settings")
    Field("Name", name) { name = it }
    Field("Phone", phone) { phone = it }
    Field("Country", country) { country = it }
    Field("Daily limit (₹)", daily) { daily = it }
    Field("Monthly limit (₹)", monthly) { monthly = it }
    Field("User-entered opening balance (₹)", opening) { opening = it }
    Choice("Appearance", theme, accents.keys.toList()) { theme = it }
    Choice("Display mode", mode, listOf("System", "Light", "Dark")) { mode = it }
    FinoraTheme(theme, mode) {
        Button(
            {
                m.write(done) {
                    m.update(
                        mapOf(
                            "name" to name,
                            "phone" to phone,
                            "country" to country,
                            "daily" to paise(daily),
                            "monthly" to paise(monthly),
                            "opening" to paise(opening),
                            "theme" to theme,
                            "mode" to mode,
                        )
                    )
                }
            },
            enabled =
                paise(daily) != null &&
                    paise(monthly) != null &&
                    (opening.isBlank() || paise(opening) != null),
        ) {
            Text("Save preferences")
        }
    }
}

@Composable
fun Planning(m: LedgerModel) {
    val plans by m.plans.collectAsState()
    var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var kind by rememberSaveable { mutableStateOf("Bill") }
    var title by rememberSaveable { mutableStateOf("") }
    var amt by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var clock by rememberSaveable { mutableStateOf("09:00") }
    var frequency by rememberSaveable { mutableStateOf("Once") }
    val ctx = LocalContext.current
    val permission =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) {}
    Heading("Financial calendar")
    if (
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
    )
        OutlinedButton({ permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("Allow reminder notifications")
        }
    Field("Reminder time (HH:mm)", clock) { clock = it }
    Choice("Repeat", frequency, listOf("Once", "Daily", "Weekly", "Monthly")) { frequency = it }
    Choice("Type", kind, listOf("Bill", "Subscription", "Savings goal", "Reminder")) { kind = it }
    Field("Name", title) { title = it }
    Field("Amount (₹)", amt) { amt = it }
    Field("Due date (YYYY-MM-DD)", due) { due = it }
    Button(
        {
            val plan =
                Plan(
                    id,
                    title.trim(),
                    paise(amt)!!,
                    due,
                    kind,
                    clock = clock,
                    frequency = frequency,
                )
            m.write({
                id = UUID.randomUUID().toString()
                title = ""
                amt = ""
            }) {
                m.root
                    .collection("plans")
                    .document(plan.id)
                    .set(plan.copy(progress = plans.find { it.id == plan.id }?.progress ?: 0))
                    .await()
            }
        },
        enabled =
            title.isNotBlank() &&
                (paise(amt) ?: 0) > 0 &&
                runCatching { LocalDateTime.parse(due + "T" + clock) }.isSuccess,
    ) {
        Text("Save")
    }
    plans
        .sortedBy { it.due }
        .forEach { p ->
            Panel {
                Heading(p.title)
                Text(p.kind + " · " + p.due + " · " + money(p.amount))
                val ref = m.root.collection("plans").document(p.id)
                if (p.kind == "Savings goal") {
                    var add by remember(p.id) { mutableStateOf("") }
                    Text("Saved · " + money(p.progress))
                    Field("Contribution (₹)", add) { add = it }
                    Button(
                        {
                            val amount = paise(add)!!
                            add = ""
                            m.write {
                                ref.update(
                                        "progress",
                                        com.google.firebase.firestore.FieldValue.increment(amount),
                                    )
                                    .await()
                            }
                        },
                        enabled = (paise(add) ?: 0) > 0,
                    ) {
                        Text("Add contribution")
                    }
                } else if (p.progress == 0L)
                    Button({
                        m.write {
                            val tx =
                                m.root
                                    .collection("transactions")
                                    .document("plan-" + p.id + "-" + p.due)
                            m.root.firestore
                                .runTransaction { t ->
                                    val current = t.get(ref)
                                    require(
                                        current.exists() &&
                                            current.getLong("progress") == 0L &&
                                            current.getString("due") == p.due
                                    )
                                    t.set(
                                        tx,
                                        Entry(
                                            tx.id,
                                            p.title,
                                            p.amount,
                                            category =
                                                if (p.kind == "Reminder") "Others" else "Bills",
                                            time = System.currentTimeMillis(),
                                            fixed = p.kind != "Reminder",
                                            source =
                                                if (p.kind == "Reminder") "Reminder" else "Manual",
                                        ),
                                    )
                                    val day = LocalDate.parse(p.due)
                                    val next =
                                        when (p.frequency) {
                                            "Daily" -> day.plusDays(1)
                                            "Weekly" -> day.plusWeeks(1)
                                            "Monthly" -> day.plusMonths(1)
                                            else -> null
                                        }
                                    if (next == null) t.update(ref, "progress", p.amount)
                                    else t.update(ref, "due", next.toString())
                                }
                                .await()
                        }
                    }) {
                        Text("Confirm payment")
                    }
                else Text("Paid")
                if (p.kind != "Savings goal")
                    TextButton({ m.write { ref.update("enabled", !p.enabled).await() } }) {
                        Text(if (p.enabled) "Pause reminders" else "Resume reminders")
                    }
                TextButton({
                    id = p.id
                    title = p.title
                    amt = BigDecimal.valueOf(p.amount, 2).toPlainString()
                    due = p.due
                    kind = p.kind
                    clock = p.clock
                    frequency = p.frequency
                }) {
                    Text("Edit")
                }
                TextButton({ m.write { ref.delete().await() } }) { Text("Remove plan") }
            }
        }
}

@Composable
fun Parent(m: LedgerModel) {
    val ref = remember(m) { m.root.collection("reportPreferences").document("daily") }
    var phone by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("21:00") }
    var zone by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var consent by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(emptyList<String>()) }
    var relationship by remember { mutableStateOf("") }
    LaunchedEffect(m) {
        try {
            val d = ref.get().await()
            phone = d.getString("recipient").orEmpty()
            time = d.getString("time") ?: time
            zone = d.getString("zone") ?: zone
            consent = d.getBoolean("consent") == true
            enabled = d.getBoolean("enabled") == true
            fields = (d.get("fields") as? List<*>)?.filterIsInstance<String>().orEmpty()
            parent = d.getString("name").orEmpty()
            relationship = d.getString("relationship").orEmpty()
            ready = true
        } catch (e: Exception) {
            feedback = e.localizedMessage.orEmpty()
        }
    }
    Heading("FINORA Parent Connect")
    Text(
        "Share a daily summary of the preceding 24 hours. Delivery requires recipient verification and an approved WhatsApp template."
    )
    Field("Parent or guardian name", parent) { parent = it }
    Field("Relationship", relationship) { relationship = it }
    Field("WhatsApp number (+country code)", phone) {
        phone = it
        consent = false
        enabled = false
    }
    Field("Report time (HH:mm)", time) { time = it }
    Field("Time zone", zone) { zone = it }
    Row {
        Checkbox(
            consent,
            {
                consent = it
                if (!it) enabled = false
            },
        )
        Text("I consent to sharing the totals below with this recipient.")
    }
    Row {
        Checkbox(enabled, { enabled = it }, enabled = consent)
        Text("Schedule daily report")
    }
    Text("Optional report details")
    listOf("categories", "budget", "balances", "transactions").forEach { f ->
        Row {
            Checkbox(f in fields, { checked -> fields = if (checked) fields + f else fields - f })
            Text(f.replaceFirstChar { it.uppercase() })
        }
    }
    val client = LocalReports.current
    var preview by remember { mutableStateOf<StatementPreview?>(null) }
    OutlinedButton({ m.write { preview = client.request { a, b -> preview(a, b) } } }) {
        Text("Preview saved report settings on Render")
    }
    preview?.let { report ->
        Panel {
            Heading("Last 24 hours")
            Text(
                Instant.ofEpochMilli(report.start).atZone(ZoneId.systemDefault()).toString() +
                    " – " +
                    Instant.ofEpochMilli(report.end).atZone(ZoneId.systemDefault())
            )
            Text("Income · " + money(report.income))
            Text("Expenses · " + money(report.expenses))
            Text("${report.count} transactions")
            report.extra?.let { Text(it) }
        }
    }
    BotLog(m.uid)
    Button(
        {
            m.write({ feedback = "Report preferences saved" }) {
                ref.set(
                        mapOf(
                            "name" to parent,
                            "relationship" to relationship,
                            "fields" to fields,
                            "enabled" to enabled,
                            "consent" to consent,
                            "recipient" to phone,
                            "time" to time,
                            "zone" to zone,
                        )
                    )
                    .await()
            }
        },
        enabled =
            ready &&
                phone.matches(Regex("""\+[1-9]\d{7,14}""")) &&
                time.matches(Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")) &&
                runCatching { ZoneId.of(zone) }.isSuccess,
    ) {
        Text("Save report settings")
    }
    var status by remember { mutableStateOf<IntegrationStatus?>(null) }
    var challenge by remember { mutableStateOf<Challenge?>(null) }
    OutlinedButton({ m.write { status = client.request { a, b -> status(a, b) } } }) {
        Text("Check integration & delivery history")
    }
    status?.let { s ->
        Text(if (s.verified) "Recipient verified" else "Recipient verification required")
        Text(if (s.enabled) "Service sending enabled" else "Service sending paused")
        s.history.forEach {
            Text(
                Instant.ofEpochMilli(it.end)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString() + " · " + (it.delivery ?: it.state)
            )
        }
    }
    OutlinedButton(
        { m.write { challenge = client.request { a, b -> challenge(a, b) } } },
        enabled = ready && consent,
    ) {
        Text("Get recipient verification code")
    }
    challenge?.let {
        Panel {
            Text(
                "Ask the recipient to send this exact text to " +
                    it.businessNumber +
                    " on WhatsApp within 10 minutes."
            )
            androidx.compose.foundation.text.selection.SelectionContainer { Text(it.text) }
        }
    }
    var removing by remember { mutableStateOf(false) }
    TextButton({ removing = true }) { Text("Remove recipient") }
    if (removing)
        AlertDialog(
            { removing = false },
            title = { Text("Remove recipient and stop reports?") },
            confirmButton = {
                TextButton({
                    removing = false
                    m.write({
                        phone = ""
                        parent = ""
                        enabled = false
                        consent = false
                        challenge = null
                        status = null
                        feedback = "Recipient removed"
                    }) {
                        ref.delete().await()
                    }
                }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton({ removing = false }) { Text("Cancel") } },
        )
    Text(feedback)
}
