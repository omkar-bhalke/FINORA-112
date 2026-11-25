package com.finora.app

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

fun scheduleReminder(ctx: Context, uid: String, p: Plan) {
    val manager = WorkManager.getInstance(ctx)
    val name = "plan-$uid-" + p.id
    if (!p.enabled || p.kind == "Savings goal" || p.progress > 0) {
        manager.cancelUniqueWork(name)
        return
    }
    val at =
        LocalDateTime.parse(p.due + "T" + p.clock)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    if (at < System.currentTimeMillis()) return
    val req =
        OneTimeWorkRequestBuilder<PlanReminder>()
            .setInitialDelay(
                (at - System.currentTimeMillis()).coerceAtLeast(0),
                TimeUnit.MILLISECONDS,
            )
            .setInputData(workDataOf("uid" to uid, "id" to p.id, "due" to p.due))
            .addTag("plans-$uid")
            .build()
    manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
}

class PlanReminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
        val id = inputData.getString("id") ?: return Result.failure()
        val p =
            try {
                FirebaseFirestore.getInstance()
                    .document("users/$uid/plans/$id")
                    .get()
                    .await()
                    .toObject(Plan::class.java)
            } catch (e: Exception) {
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        if (p == null || !p.enabled || p.progress > 0 || p.due != inputData.getString("due"))
            return Result.success()
        val ctx = applicationContext
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "plans",
                "Bills and expense reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return Result.success()
        val open =
            PendingIntent.getActivity(
                ctx,
                0,
                Intent(ctx, MainActivity::class.java).putExtra("planning", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            id.hashCode(),
            NotificationCompat.Builder(ctx, "plans")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("FINORA reminder")
                .setContentText("Did you spend money on " + p.title + "?")
                .setContentIntent(open)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }
}
