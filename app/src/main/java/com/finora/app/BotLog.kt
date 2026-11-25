package com.finora.app

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.google.firebase.database.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BotLog(uid: String) {
    var rows by remember(uid) { mutableStateOf(emptyList<DataSnapshot>()) }
    var message by remember(uid) { mutableStateOf("Loading report activity…") }
    DisposableEffect(uid) {
        val query =
            FirebaseDatabase.getInstance()
                .getReference("reportActivity")
                .child(uid)
                .orderByChild("scheduledAt")
                .limitToLast(30)
        val listener =
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    rows = snapshot.children.toList().asReversed()
                    message = if (rows.isEmpty()) "No statement activity recorded yet." else ""
                }

                override fun onCancelled(error: DatabaseError) {
                    rows = emptyList()
                    message = "Report log unavailable · " + error.message
                }
            }
        query.addValueEventListener(listener)
        onDispose { query.removeEventListener(listener) }
    }
    Heading("Statement bot log")
    if (message.isNotEmpty()) Text(message)
    rows.forEach { row ->
        val zone =
            runCatching { ZoneId.of(row.child("zone").getValue(String::class.java)) }
                .getOrDefault(ZoneId.systemDefault())
        val format = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z").withZone(zone)
        Panel {
            Text("Scheduled report · " + row.key)
            listOf(
                    "scheduledAt" to "Scheduled",
                    "generatedAt" to "Statement generated",
                    "acceptedAt" to "WhatsApp accepted",
                    "deliveryAt" to "Delivery update",
                )
                .forEach { (key, label) ->
                    row.child(key).getValue(Long::class.java)?.let {
                        Text(label + " · " + format.format(Instant.ofEpochMilli(it)))
                    }
                }
            Text(
                "Status · " + (row.child("delivery").value ?: row.child("state").value ?: "Unknown")
            )
            Text("Send attempts · " + (row.child("attempts").value ?: 0))
        }
    }
}
