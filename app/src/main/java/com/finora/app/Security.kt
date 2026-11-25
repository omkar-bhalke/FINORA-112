package com.finora.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

@Composable
fun Avatar(m: LedgerModel, editable: Boolean) {
    val ctx = LocalContext.current
    val ref =
        remember(m.uid) {
            FirebaseStorage.getInstance().reference.child("users/" + m.uid + "/avatar.jpg")
        }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(m) {
        runCatching {
            val data = ref.getBytes(700000).await()
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                m.write {
                    val encoded =
                        withContext(Dispatchers.IO) {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            ctx.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it, null, opts)
                            }
                            require(opts.outWidth > 0 && opts.outHeight > 0) { "Unsupported image" }
                            opts.inJustDecodeBounds = false
                            opts.inSampleSize = 1
                            while (
                                maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > 512
                            ) opts.inSampleSize *= 2
                            val img =
                                ctx.contentResolver.openInputStream(uri)?.use {
                                    BitmapFactory.decodeStream(it, null, opts)
                                } ?: error("Cannot read photo")
                            ByteArrayOutputStream().use {
                                img.compress(Bitmap.CompressFormat.JPEG, 82, it)
                                it.toByteArray()
                            }
                        }
                    ref.putBytes(
                            encoded,
                            StorageMetadata.Builder().setContentType("image/jpeg").build(),
                        )
                        .await()
                    bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
                }
        }
    bitmap?.let { Image(it.asImageBitmap(), "Profile photo", Modifier.size(64.dp)) }
    if (editable) TextButton({ picker.launch("image/*") }) { Text("Change profile photo") }
}

@Composable
fun Security(m: LedgerModel) {
    val ctx = LocalContext.current
    val client = LocalReports.current
    var dialog by remember { mutableStateOf(false) }
    val user = FirebaseAuth.getInstance().currentUser
    Heading("Privacy & security")
    Text(user?.email.orEmpty())
    Text(
        "Signed in with " +
            user
                ?.providerData
                ?.map { it.providerId }
                ?.filter { it != "firebase" }
                ?.joinToString()
                .orEmpty()
    )
    user?.metadata?.let {
        Text(
            "Account created · " +
                Instant.ofEpochMilli(it.creationTimestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
        )
    }
    Text(
        "Notification candidates stay on this device until you confirm import. You can delete saved imports in Transactions."
    )
    OutlinedButton({ m.write { LocalLedger.get(ctx).reviews().clear(m.uid) } }) {
        Text("Clear local review queue")
    }
    TextButton({ dialog = true }) { Text("Delete account and financial records") }
    if (dialog)
        AlertDialog(
            { dialog = false },
            title = { Text("Permanently delete your account?") },
            text = {
                Text(
                    "This removes your financial records, profile and report preferences. Sign in again first if your session is older than five minutes."
                )
            },
            confirmButton = {
                TextButton({
                    dialog = false
                    m.write {
                        client.request { a, b -> deleteAccount(a, b) }
                        LocalLedger.get(ctx).reviews().clear(m.uid)
                        ctx.inbox(m.uid).edit().clear().apply()
                        FirebaseAuth.getInstance().signOut()
                    }
                }) {
                    Text("Delete permanently")
                }
            },
            dismissButton = { TextButton({ dialog = false }) { Text("Cancel") } },
        )
}
