package com.finora.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID
import kotlinx.coroutines.tasks.await

data class BankAccount(
    val id: String = "",
    val bank: String = "",
    val nickname: String = "",
    val lastFour: String = "",
    val opening: Long = 0,
)

@Composable
fun Accounts(m: LedgerModel, p: Profile, rows: List<Entry>) {
    val accounts by m.accounts.collectAsState()
    Heading("Connected accounts")
    Text("Bank balance synchronization requires authorized bank integration.")
    accounts.forEach { a ->
        Panel {
            Heading(a.nickname)
            Text(a.bank + " · •••• " + a.lastFour)
            val linked = rows.filter { it.account == a.id }
            Text("User-entered opening balance · " + money(a.opening))
            Text(
                "Calculated balance · " +
                    money(
                        a.opening + linked.filter { it.credit }.sumOf { it.amount } - linked.spent()
                    )
            )
        }
    }
    if (p.banks.isEmpty()) {
        Text("Select a bank before adding an account.")
        return
    }
    var bank by rememberSaveable { mutableStateOf(p.banks.first()) }
    var title by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("") }
    var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    Heading("Add manual account")
    Choice("Bank", bank, p.banks) { bank = it }
    Field("Account nickname", title) { title = it }
    Field("Last four digits only", digits) {
        if (it.length <= 4 && it.all(Char::isDigit)) digits = it
    }
    Field("Opening balance (₹)", opening) { opening = it }
    Button(
        {
            val a = BankAccount(id, bank, title.trim(), digits, paise(opening)!!)
            m.write({
                id = UUID.randomUUID().toString()
                title = ""
                digits = ""
                opening = ""
            }) {
                m.root.collection("bankAccounts").document(a.id).set(a).await()
            }
        },
        enabled = title.isNotBlank() && digits.length == 4 && paise(opening) != null,
    ) {
        Text("Save account")
    }
}

@Composable
fun Categories(m: LedgerModel) {
    val custom by m.custom.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf("") }
    Heading("Custom categories")
    Field("Category name", name) { name = it }
    Button(
        {
            val ref =
                m.root
                    .collection("categories")
                    .document(editing.ifBlank { UUID.randomUUID().toString() })
            m.write({
                name = ""
                editing = ""
            }) {
                ref.set(mapOf("name" to name.trim())).await()
            }
        },
        enabled = name.trim().length in 1..40 && name.trim() !in categories,
    ) {
        Text("Save category")
    }
    custom.forEach { (id, title) ->
        Panel {
            Text(title)
            TextButton({
                editing = id
                name = title
            }) {
                Text("Edit")
            }
            TextButton({
                m.write { m.root.collection("categories").document(id).delete().await() }
            }) {
                Text("Delete")
            }
        }
    }
}
