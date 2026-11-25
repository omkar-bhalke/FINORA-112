package com.finora.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

val payments =
    mapOf(
        "Google Pay" to "com.google.android.apps.nbu.paisa.user",
        "PhonePe" to "com.phonepe.app",
        "Paytm" to "net.one97.paytm",
    )

@Composable
fun Spending(p: Profile, rows: List<Entry>, m: LedgerModel) {
    val ctx = LocalContext.current
    val spent = rows.filter { it.date() == LocalDate.now() && !it.fixed }.spent()
    val reached = p.daily > 0 && spent >= p.daily
    var confirm by remember { mutableStateOf("") }
    Heading("Spending control")
    Panel {
        Text("Today · " + money(spent))
        Text("Daily limit · " + money(p.daily))
        Text(
            when {
                p.daily <= 0 -> "Set your daily limit in Profile"
                reached -> "Your daily spending limit has been reached."
                else -> "Remaining · " + money(p.daily - spent)
            }
        )
    }
    Choice("Mode", p.control, listOf("Off", "Reminder", "Warning", "Confirm before opening")) {
        m.write { m.update(mapOf("control" to it)) }
    }
    Text("Controls apply when opening a selected payment app from FINORA.")
    payments.forEach { (name, pkg) ->
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
        Panel {
            Text(name)
            if (launch == null) Text("Not detected")
            else {
                Row {
                    Checkbox(
                        pkg in p.payments,
                        { checked ->
                            m.write {
                                m.update(
                                    mapOf(
                                        "payments" to
                                            (if (checked) p.payments + pkg else p.payments - pkg)
                                                .distinct()
                                    )
                                )
                            }
                        },
                    )
                    Text("Apply spending control")
                }
                OutlinedButton({
                    if (
                        reached &&
                            pkg in p.payments &&
                            p.control in listOf("Warning", "Confirm before opening")
                    )
                        confirm = pkg
                    else ctx.startActivity(launch)
                }) {
                    Text("Open " + name)
                }
            }
        }
    }
    if (confirm.isNotEmpty())
        AlertDialog(
            { confirm = "" },
            title = { Text("Daily limit reached") },
            text = {
                Text(
                    "Today's recorded spending is " +
                        money(spent) +
                        ". Continue to the payment app?"
                )
            },
            confirmButton = {
                TextButton({
                    ctx.packageManager.getLaunchIntentForPackage(confirm)?.let(ctx::startActivity)
                    confirm = ""
                }) {
                    Text("Continue anyway")
                }
            },
            dismissButton = { TextButton({ confirm = "" }) { Text("Cancel") } },
        )
}
