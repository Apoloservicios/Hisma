package com.example.hisma.utils

import android.util.Log
import com.example.hisma.model.Subscription
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Clase utilitaria para gestionar las suscripciones del lubricentro
 */
class SubscriptionManager(private val db: FirebaseFirestore) {

    private val TAG = "SubscriptionManager"

    /**
     * Obtiene la suscripción actual del lubricentro
     */
    suspend fun getCurrentSubscription(lubricentroId: String): Subscription? {
        return try {
            val subscriptionDoc = db.collection("lubricentros")
                .document(lubricentroId)
                .collection("subscription")
                .document("current")
                .get()
                .await()

            if (subscriptionDoc.exists()) {
                val subscription = subscriptionDoc.toObject(Subscription::class.java)
                subscription?.copy(id = subscriptionDoc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo suscripción actual", e)
            null
        }
    }

    /**
     * Verifica si la suscripción actual es válida
     */
    suspend fun checkCurrentSubscription(lubricentroId: String): Boolean {
        val subscription = getCurrentSubscription(lubricentroId)
        return subscription?.isValid() ?: false
    }

    /**
     * Decrementa el contador de cambios disponibles
     */
    suspend fun decrementAvailableChanges(lubricentroId: String): Boolean {
        try {
            val subscriptionRef = db.collection("lubricentros")
                .document(lubricentroId)
                .collection("subscription")
                .document("current")

            val subscriptionDoc = subscriptionRef.get().await()

            if (!subscriptionDoc.exists()) {
                return false
            }

            val availableChanges = subscriptionDoc.getLong("availableChanges") ?: 0
            val changesUsed = subscriptionDoc.getLong("changesUsed") ?: 0

            if (availableChanges <= 0) {
                return false
            }

            // Actualizar suscripción
            val updateMap = mapOf(
                "availableChanges" to (availableChanges - 1),
                "changesUsed" to (changesUsed + 1)
            )

            subscriptionRef.update(updateMap).await()
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error decrementando cambios disponibles", e)
            return false
        }
    }

    /**
     * Crea una suscripción de prueba para un nuevo lubricentro
     */
    suspend fun createTrialSubscription(lubricentroId: String): Boolean {
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 7) // 7 días de prueba

            val subscription = Subscription(
                lubricentroId = lubricentroId,
                planId = "trial",
                startDate = Timestamp.now(),
                endDate = Timestamp(calendar.time),
                active = true,
                valid = true,
                totalChangesAllowed = 10,
                changesUsed = 0,
                availableChanges = 10,
                isPaqueteAdicional = false
            )

            db.collection("lubricentros")
                .document(lubricentroId)
                .collection("subscription")
                .document("current")
                .set(subscription)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creando suscripción de prueba", e)
            false
        }
    }
}