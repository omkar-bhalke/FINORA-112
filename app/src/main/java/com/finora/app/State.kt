package com.finora.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LedgerModel(val uid: String) : ViewModel() {
    val root = FirebaseFirestore.getInstance().collection("users").document(uid)
    val profile = MutableStateFlow<Profile?>(null)
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val plans = MutableStateFlow<List<Plan>>(emptyList())
    val accounts = MutableStateFlow<List<BankAccount>>(emptyList())
    val custom = MutableStateFlow<Map<String, String>>(emptyMap())
    val error = MutableStateFlow("")
    private val watches =
        listOf(
            root.collection("bankAccounts").addSnapshotListener { s, e ->
                if (e != null) error.value = e.localizedMessage.orEmpty()
                else
                    accounts.value =
                        s!!.documents.mapNotNull { it.toObject(BankAccount::class.java) }
            },
            root.collection("categories").addSnapshotListener { s, e ->
                if (e != null) error.value = e.localizedMessage.orEmpty()
                else
                    custom.value =
                        s!!.documents.associate { it.id to it.getString("name").orEmpty() }
            },
            root.addSnapshotListener { s, e ->
                if (e != null) error.value = e.localizedMessage.orEmpty()
                else if (s != null) profile.value = s.toObject(Profile::class.java) ?: Profile()
            },
            root.collection("transactions").addSnapshotListener { s, e ->
                if (e != null) error.value = e.localizedMessage.orEmpty()
                else
                    entries.value =
                        s!!
                            .documents
                            .mapNotNull { d -> d.toObject(Entry::class.java)?.copy(id = d.id) }
                            .sortedByDescending { it.time }
            },
            root.collection("plans").addSnapshotListener { s, e ->
                if (e != null) error.value = e.localizedMessage.orEmpty()
                else
                    plans.value =
                        s!!.documents.mapNotNull { d ->
                            d.toObject(Plan::class.java)?.copy(id = d.id)
                        }
            },
        )

    fun write(done: () -> Unit = {}, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                done()
            } catch (e: Exception) {
                error.value = e.localizedMessage ?: "Save failed"
            }
        }
    }

    suspend fun update(data: Map<String, Any?>) {
        root.set(data, SetOptions.merge()).await()
    }

    suspend fun save(entry: Entry) {
        require(entry.amount > 0 && entry.title.isNotBlank())
        root.collection("transactions").document(entry.id).set(entry).await()
    }

    fun close() {
        watches.forEach { it.remove() }
        viewModelScope.cancel()
    }

    override fun onCleared() {
        close()
    }
}
