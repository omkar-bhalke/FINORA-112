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

interface ReportsApi {
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
        require(BuildConfig.API_URL.isNotBlank()) {
            "Report service URL is not configured for this build."
        }
        val jwt =
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: error("Please sign in again")
        val check = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        val api =
            Retrofit.Builder()
                .baseUrl(BuildConfig.API_URL + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ReportsApi::class.java)
        return api.call("Bearer " + jwt, check)
    }
}
