package com.example.hisma.utils

import android.content.Context
import android.util.Log
import com.example.hisma.model.Subscription
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class SubscriptionManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val TAG = "SubscriptionManager"

    suspend fun checkSubscriptionStatus(): SubscriptionStatus {
        try {
            val currentUser = auth.currentUser ?: return SubscriptionStatus.NOT_AUTHENTICATED
            Log.d(TAG, "Verificando estado de suscripción para: ${currentUser.uid}")

            val lubDoc = db.collection("lubricentros")
                .document(currentUser.uid)
                .get()
                .await()

            if (!lubDoc.exists()) {
                Log.d(TAG, "Documento no encontrado")
                return SubscriptionStatus.NOT_FOUND
            }

            val lubricentro = lubDoc.toObject(com.example.hisma.model.Lubricentro::class.java)
            val subscription = lubricentro?.subscription

            if (subscription == null) {
                Log.d(TAG, "Suscripción nula")
                return SubscriptionStatus.NOT_FOUND
            }

            Log.d(TAG, "Datos de suscripción: active=${subscription.active}, valid=${subscription.valid || subscription.calculateIsValid()}, " +
                    "endDate=${subscription.endDate?.toDate()}, " +
                    "changesUsed=${subscription.changesUsed}/${subscription.totalChangesAllowed}")

            // Verificar si la suscripción está activa directamente desde el campo
            if (!subscription.active) {
                Log.d(TAG, "Suscripción marcada como inactiva")
                return SubscriptionStatus.INACTIVE
            }

            // Verificar si está dentro del período válido
            val now = Timestamp.now()
            if (now.compareTo(subscription.endDate) > 0) {
                return SubscriptionStatus.EXPIRED
            }

            // Verificar si hay cambios disponibles
            if (subscription.totalChangesAllowed > 0 &&
                subscription.changesUsed >= subscription.totalChangesAllowed) {
                return SubscriptionStatus.NO_CHANGES_LEFT
            }

            Log.d(TAG, "Suscripción ACTIVA y válida")
            return SubscriptionStatus.ACTIVE
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando suscripción", e)
            return SubscriptionStatus.ERROR
        }
    }

    suspend fun incrementChangesUsed(): Boolean {
        try {
            val currentUser = auth.currentUser ?: return false
            Log.d(TAG, "Incrementando cambios usados para: ${currentUser.uid}")

            // Obtener la suscripción actual
            val lubDoc = db.collection("lubricentros")
                .document(currentUser.uid)
                .get()
                .await()

            if (!lubDoc.exists()) {
                Log.e(TAG, "Documento no encontrado")
                return false
            }

            // Obtener objetos actuales
            val lubricentro = lubDoc.toObject(com.example.hisma.model.Lubricentro::class.java)
            val subscription = lubricentro?.subscription

            if (subscription == null) {
                Log.e(TAG, "Suscripción nula")
                return false
            }

            // Valores actuales
            val currentUsed = subscription.changesUsed
            val currentAvailable = subscription.availableChanges

            Log.d(TAG, "Valores actuales: usados=$currentUsed, disponibles=$currentAvailable")

            // Incrementar cambios usados
            val newUsed = currentUsed + 1

            // Calcular nuevo valor de disponibles
            val newAvailable = if (subscription.totalChangesAllowed > 0) {
                (subscription.totalChangesAllowed - newUsed).coerceAtLeast(0)
            } else {
                currentAvailable
            }

            Log.d(TAG, "Nuevos valores: usados=$newUsed, disponibles=$newAvailable")

            // Crear un mapa con solo los campos a actualizar
            val updates = mapOf(
                "subscription.changesUsed" to newUsed,
                "subscription.availableChanges" to newAvailable
            )

            // Usar una actualización atómica en lugar de reemplazar toda la suscripción
            db.collection("lubricentros")
                .document(currentUser.uid)
                .update(updates)
                .await()

            Log.d(TAG, "Cambios usados incrementados correctamente")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementando cambios usados", e)
            return false
        }
    }
    // En SubscriptionManager.kt
    suspend fun activateTrial(): Boolean {
        try {
            val currentUser = auth.currentUser ?: return false

            // Verificar si ya tiene una suscripción
            val lubDoc = db.collection("lubricentros")
                .document(currentUser.uid)
                .get()
                .await()

            if (lubDoc.exists()) {
                val subscription = lubDoc.toObject(com.example.hisma.model.Lubricentro::class.java)?.subscription

                if (subscription != null && subscription.trialActivated) {
                    // Ya activó su prueba gratuita
                    return false
                }
            }

            // Crear suscripción de prueba
            val trialSubscription = Subscription.createTrial()

            // Actualizar en Firestore
            db.collection("lubricentros")
                .document(currentUser.uid)
                .update("subscription", trialSubscription)
                .await()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error activando prueba gratuita", e)
            return false
        }
    }

    enum class SubscriptionStatus {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        NO_CHANGES_LEFT,
        NOT_FOUND,
        NOT_AUTHENTICATED,
        ERROR
    }
}