package com.finora.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

@HiltAndroidApp class FinoraApplication : Application()

data class Challenge(val text: String, val businessNumber: String)

data class Delivery(val state: String, val delivery: String?, val end: Long)

data class IntegrationStatus(
    val verified: Boolean,
    val enabled: Boolean,
    val history: List<Delivery>,
)

data class StatementPreview(
    val start: Long,
    val end: Long,
    val income: Long,
    val expenses: Long,
    val count: Int,
    val extra: String?,
)

interface ReportsApi {
    @GET("v1/preview")
    suspend fun preview(
        @Header("Authorization") auth: String,
        @Header("X-Firebase-AppCheck") app: String,
    ): StatementPreview

    @DELETE("v1/account")
    suspend fun deleteAccount(
        @Header("Authorization") auth: String,
        @Header("X-Firebase-AppCheck") app: String,
    )

    @GET("v1/status")
    suspend fun status(
        @Header("Authorization") auth: String,
        @Header("X-Firebase-AppCheck") app: String,
    ): IntegrationStatus

    @POST("v1/recipient-challenge")
    suspend fun challenge(
        @Header("Authorization") auth: String,
        @Header("X-Firebase-AppCheck") app: String,
    ): Challenge
}

class ReportClient @javax.inject.Inject constructor() {
    suspend fun <T> request(call: suspend ReportsApi.(String, String) -> T): T {
        val origin =
            BuildConfig.API_URL.ifEmpty {
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("serviceConfig/reportApiUrl")
                        .get()
                        .await()
                        .getValue(String::class.java)
                        .orEmpty()
                }
                .trimEnd('/')
        require(Regex("""https://[A-Za-z0-9.-]+""").matches(origin)) {
            "Configure your Render HTTPS address in Firebase serviceConfig/reportApiUrl."
        }
        val jwt =
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: error("Please sign in again")
        val check = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        val api =
            Retrofit.Builder()
                .baseUrl(origin + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ReportsApi::class.java)
        return api.call("Bearer " + jwt, check)
    }
}
