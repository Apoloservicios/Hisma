package com.example.hisma.utils

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class SubscriptionController(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun checkSubscriptionStatus(): SubscriptionStatus {
        try {
            val currentUser = auth.currentUser ?: return SubscriptionStatus.NOT_AUTHENTICATED

            // Verificar trial activo
            val lubDoc = db.collection("lubricentros").document(currentUser.uid).get().await()
            val trialUsed = lubDoc.getBoolean("trialUsed") ?: false
            val trialExpiration = lubDoc.getTimestamp("trialExpiration")

            if (!trialUsed || (trialExpiration != null && trialExpiration > Timestamp.now())) {
                // El usuario aún tiene trial válido
                return SubscriptionStatus.ACTIVE_TRIAL
            }

            // Buscar suscripciones activas
            val subscriptionsSnapshot = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", currentUser.uid)
                .whereEqualTo("estado", "activa")
                .get()
                .await()

            if (subscriptionsSnapshot.isEmpty) {
                return SubscriptionStatus.NO_SUBSCRIPTION
            }

            // Verificar suscripciones activas y sus límites
            val subscriptions = subscriptionsSnapshot.documents.mapNotNull { doc ->
                try {
                    val tipo = doc.getString("tipo") ?: "principal"
                    val fechaFin = doc.getTimestamp("fechaFin") ?: Timestamp.now()
                    val cambiosRestantes = doc.getLong("cambiosRestantes")?.toInt() ?: 0

                    Triple(tipo, fechaFin, cambiosRestantes)
                } catch (e: Exception) {
                    Log.e("SubscriptionController", "Error mapping subscription: ${e.message}")
                    null
                }
            }

            // Si hay alguna suscripción principal activa y válida
            val principalSub = subscriptions.firstOrNull {
                it.first == "principal" && it.second > Timestamp.now()
            }

            if (principalSub == null) {
                return SubscriptionStatus.EXPIRED_SUBSCRIPTION
            }

            // Calcular cambios disponibles totales
            val totalChanges = subscriptions.sumOf { it.third }

            return when {
                totalChanges <= 0 -> SubscriptionStatus.NO_CHANGES_LEFT
                totalChanges < 10 -> SubscriptionStatus.LOW_CHANGES
                principalSub.second.toDate().time - System.currentTimeMillis() < 7 * 24 * 60 * 60 * 1000 ->
                    SubscriptionStatus.EXPIRING_SOON
                else -> SubscriptionStatus.ACTIVE
            }
        } catch (e: Exception) {
            Log.e("SubscriptionController", "Error checking subscription: ${e.message}")
            return SubscriptionStatus.ERROR
        }
    }
}

enum class SubscriptionStatus {
    NOT_AUTHENTICATED,
    NO_SUBSCRIPTION,
    EXPIRED_SUBSCRIPTION,
    NO_CHANGES_LEFT,
    LOW_CHANGES,
    EXPIRING_SOON,
    ACTIVE,
    ACTIVE_TRIAL,
    ERROR
}