package com.example.hisma.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.hisma.model.Suscripcion
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Clase para manejar todas las operaciones relacionadas con las suscripciones
 */
class SubscriptionManager(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "SubscriptionManager"

    /**
     * Verifica si hay una suscripción activa y válida, usando callback
     */
    fun checkActiveSubscription(callback: (Result<Subscription?>) -> Unit) {
        scope.launch {
            try {
                val result = checkActiveSubscriptionSync()
                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en checkActiveSubscription: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Verifica si hay una suscripción activa y válida de forma sincrónica
     */
    suspend fun checkActiveSubscriptionSync(): Result<Subscription?> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

            Log.d(TAG, "Buscando suscripciones para lubricentro: ${currentUser.uid}")

            // Consultar colección "suscripciones"
            val snapshot = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", currentUser.uid)
                .whereEqualTo("estado", "activa")
                .orderBy("fechaFin", Query.Direction.DESCENDING)
                .get()
                .await()

            Log.d(TAG, "Suscripciones encontradas: ${snapshot.documents.size}")

            if (snapshot.isEmpty) {
                Log.d(TAG, "No se encontraron suscripciones activas")
                return Result.success(null)
            }

            // Convertir documentos a objetos Subscription
            val subscriptions = snapshot.documents.mapNotNull { doc ->
                try {
                    Log.d(TAG, "Mapeando documento ${doc.id}: ${doc.data}")

                    // Mapeo manual para mayor control y debugging
                    val id = doc.id
                    val lubricentroId = doc.getString("lubricentroId") ?: ""
                    val planId = doc.getString("planId") ?: ""
                    val startDate = doc.getTimestamp("fechaInicio") ?: Timestamp.now()
                    val endDate = doc.getTimestamp("fechaFin") ?: Timestamp.now()
                    val isActive = doc.getString("estado") == "activa"
                    val total = doc.getLong("cambiosTotales")?.toInt() ?: 0
                    val used = doc.getLong("cambiosRealizados")?.toInt() ?: 0
                    val available = doc.getLong("cambiosRestantes")?.toInt() ?: 0

                    val subscription = Subscription(
                        id = id,
                        lubricentroId = lubricentroId,
                        planId = planId,
                        startDate = startDate,
                        endDate = endDate,
                        active = isActive,
                        valid = true, // Calculamos después
                        totalChangesAllowed = total,
                        changesUsed = used,
                        availableChanges = available,
                        isPaqueteAdicional = doc.getBoolean("isPaqueteAdicional") ?: false
                    )

                    // Verificar validez
                    if (subscription.calculateIsValid()) {
                        Log.d(TAG, "Suscripción válida: ${subscription.id}")
                        subscription
                    } else {
                        Log.d(TAG, "Suscripción no válida: ${subscription.id}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error mapeando documento a Subscription: ${e.message}", e)
                    null
                }
            }

            // Filtrar y retornar
            if (subscriptions.isEmpty()) {
                Log.d(TAG, "No hay suscripciones válidas")
                Result.success(null)
            } else {
                // Obtener la principal no vencida
                val principalSub = subscriptions.firstOrNull { !it.isPaqueteAdicional }
                val adicionales = subscriptions.filter { it.isPaqueteAdicional }

                Log.d(TAG, "Suscripción principal: ${principalSub?.id}, adicionales: ${adicionales.size}")
                Result.success(principalSub)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar suscripción: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene todas las suscripciones del usuario actual
     */
    fun getAllSubscriptions(callback: (Result<List<Subscription>>) -> Unit) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                // Obtener todas las suscripciones, no solo las activas
                val snapshot = db.collection("suscripciones")
                    .whereEqualTo("lubricentroId", currentUser.uid)
                    .get()
                    .await()

                val subscriptions = snapshot.documents.mapNotNull { doc ->
                    try {
                        // Mapeo similar al de checkActiveSubscriptionSync
                        val id = doc.id
                        val lubricentroId = doc.getString("lubricentroId") ?: ""
                        val planId = doc.getString("planId") ?: ""
                        val startDate = doc.getTimestamp("fechaInicio") ?: Timestamp.now()
                        val endDate = doc.getTimestamp("fechaFin") ?: Timestamp.now()
                        val isActive = doc.getString("estado") == "activa"
                        val total = doc.getLong("cambiosTotales")?.toInt() ?: 0
                        val used = doc.getLong("cambiosRealizados")?.toInt() ?: 0
                        val available = doc.getLong("cambiosRestantes")?.toInt() ?: 0

                        Subscription(
                            id = id,
                            lubricentroId = lubricentroId,
                            planId = planId,
                            startDate = startDate,
                            endDate = endDate,
                            active = isActive,
                            valid = isActive && endDate > Timestamp.now() && available > 0,
                            totalChangesAllowed = total,
                            changesUsed = used,
                            availableChanges = available,
                            isPaqueteAdicional = doc.getBoolean("isPaqueteAdicional") ?: false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapeando suscripción: ${e.message}", e)
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    callback(Result.success(subscriptions))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener suscripciones: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Registra el uso de un cambio de aceite
     */
    fun registerChangeUsage(callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val result = registerChangeUsageSync()
                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en registerChangeUsage: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    /**
     * Registra el uso de un cambio de aceite (versión sincrónica)
     */
    suspend fun registerChangeUsageSync(): Boolean {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

            // Obtener suscripciones activas
            val snapshot = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", currentUser.uid)
                .whereEqualTo("estado", "activa")
                .get()
                .await()

            if (snapshot.isEmpty) {
                throw Exception("No hay suscripciones activas")
            }

            // Convertir a subscriptions
            val subscriptions = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val endDate = doc.getTimestamp("fechaFin") ?: Timestamp.now()
                    val available = doc.getLong("cambiosRestantes")?.toInt() ?: 0
                    val isPaqueteAdicional = doc.getBoolean("isPaqueteAdicional") ?: false

                    // Solo considerar suscripciones válidas
                    if (endDate > Timestamp.now() && available > 0) {
                        Triple(id, available, isPaqueteAdicional)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (subscriptions.isEmpty()) {
                throw Exception("No hay suscripciones con cambios disponibles")
            }

            // Primero usar paquetes adicionales
            val subsToUse = subscriptions.sortedBy {
                if (it.third) 0 else 1
            }

            // Tomar la primera con cambios disponibles
            val subToUpdate = subsToUse.firstOrNull { it.second > 0 }
                ?: throw Exception("No quedan cambios disponibles")

            // Actualizar contadores
            db.collection("suscripciones").document(subToUpdate.first)
                .update(
                    mapOf(
                        "cambiosRealizados" to com.google.firebase.firestore.FieldValue.increment(1),
                        "cambiosRestantes" to com.google.firebase.firestore.FieldValue.increment(-1L),
                        "updatedAt" to Timestamp.now()
                    )
                ).await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar uso de cambio: ${e.message}", e)
            false
        }
    }

    /**
     * Solicita una nueva suscripción
     */
    fun requestNewSubscription(
        tipoSuscripcion: String,
        isPaqueteAdicional: Boolean,
        callback: (Boolean) -> Unit
    ) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                // Crear solicitud en Firestore
                db.collection("solicitudesSuscripcion")
                    .add(
                        mapOf(
                            "lubricentroId" to currentUser.uid,
                            "tipoSuscripcion" to tipoSuscripcion,
                            "isPaqueteAdicional" to isPaqueteAdicional,
                            "estado" to "pendiente",
                            "createdAt" to Timestamp.now()
                        )
                    ).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Solicitud enviada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al solicitar suscripción: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(false)
                }
            }
        }
    }

    /**
     * Activa un período de prueba para el usuario actual
     */
    fun activateTrial(callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                // Verificar si ya tiene suscripciones
                val existingSubscriptions = db.collection("suscripciones")
                    .whereEqualTo("lubricentroId", currentUser.uid)
                    .limit(1)
                    .get()
                    .await()

                if (!existingSubscriptions.isEmpty) {
                    throw Exception("Ya tienes una suscripción activa")
                }

                // Comprobar si ya se ha usado la versión de prueba
                val userDoc = db.collection("lubricentros")
                    .document(currentUser.uid)
                    .get()
                    .await()

                val trialUsed = userDoc.getBoolean("trialUsed") ?: false
                if (trialUsed) {
                    throw Exception("Ya has usado tu período de prueba")
                }

                // Crear suscripción de prueba (30 días, 10 cambios)
                val startDate = Timestamp.now()
                val calendar = Calendar.getInstance()
                calendar.time = startDate.toDate()
                calendar.add(Calendar.DAY_OF_MONTH, 30)
                val endDate = Timestamp(calendar.time)

                // Crear documento de suscripción
                val suscripcionData = mapOf(
                    "lubricentroId" to currentUser.uid,
                    "planId" to "trial",
                    "fechaInicio" to startDate,
                    "fechaFin" to endDate,
                    "estado" to "activa",
                    "cambiosTotales" to 10,
                    "cambiosRealizados" to 0,
                    "cambiosRestantes" to 10,
                    "isPaqueteAdicional" to false,
                    "trialActivated" to true,
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )

                // Guardar suscripción
                val docRef = db.collection("suscripciones").document()
                docRef.set(suscripcionData).await()

                // Marcar que el usuario ha usado su prueba
                userDoc.reference.update("trialUsed", true).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "¡Período de prueba activado! Tienes 10 cambios por 30 días.",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al activar prueba: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(false)
                }
            }
        }
    }

    /**
     * Crea una suscripción de prueba para un nuevo usuario
     */
    suspend fun createTrial(lubricentroId: String): Result<String> {
        return try {
            // Verificar si ya tiene una suscripción activa
            val existingSubscriptions = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", lubricentroId)
                .get()
                .await()

            if (!existingSubscriptions.isEmpty) {
                return Result.failure(Exception("El usuario ya tiene suscripciones"))
            }

            // Crear suscripción de prueba (30 días, 10 cambios)
            val startDate = Timestamp.now()
            val calendar = Calendar.getInstance()
            calendar.time = startDate.toDate()
            calendar.add(Calendar.DAY_OF_MONTH, 30)
            val endDate = Timestamp(calendar.time)

            val suscripcionData = mapOf(
                "lubricentroId" to lubricentroId,
                "planId" to "trial",
                "fechaInicio" to startDate,
                "fechaFin" to endDate,
                "estado" to "activa",
                "cambiosTotales" to 10,
                "cambiosRealizados" to 0,
                "cambiosRestantes" to 10,
                "isPaqueteAdicional" to false,
                "trialActivated" to true,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )

            // Guardar suscripción
            val docRef = db.collection("suscripciones").document()
            docRef.set(suscripcionData).await()

            // Marcar que el usuario ha usado su prueba
            try {
                db.collection("lubricentros").document(lubricentroId)
                    .update("trialUsed", true)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Error al marcar trialUsed, posiblemente no existe el campo: ${e.message}")
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear suscripción de prueba: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene un resumen del estado de suscripción
     */
    suspend fun getSubscriptionSummary(): Map<String, Any> {
        try {
            val subscriptionResult = checkActiveSubscriptionSync()
            if (subscriptionResult.isFailure) {
                return mapOf(
                    "suscripcionActiva" to false,
                    "error" to (subscriptionResult.exceptionOrNull()?.message ?: "Error desconocido")
                )
            }

            val subscription = subscriptionResult.getOrNull()
            if (subscription == null) {
                return mapOf("suscripcionActiva" to false)
            }

            // Obtener todas las suscripciones activas
            val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")
            val snapshot = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", currentUser.uid)
                .whereEqualTo("estado", "activa")
                .get()
                .await()

            // Calcular cambios totales disponibles
            var totalCambiosRestantes = 0
            for (doc in snapshot.documents) {
                val cambiosRestantes = doc.getLong("cambiosRestantes")?.toInt() ?: 0
                totalCambiosRestantes += cambiosRestantes
            }

            // Calcular días restantes
            val hoy = Timestamp.now()
            val diasRestantes = if (subscription.endDate > hoy) {
                val millisHastaVencimiento = subscription.endDate.toDate().time - hoy.toDate().time
                (millisHastaVencimiento / (1000 * 60 * 60 * 24)).toInt()
            } else {
                0
            }

            return mapOf(
                "suscripcionActiva" to true,
                "diasRestantes" to diasRestantes,
                "cambiosRestantes" to totalCambiosRestantes,
                "fechaVencimiento" to subscription.endDate,
                "planId" to subscription.planId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo resumen de suscripción: ${e.message}", e)
            return mapOf(
                "suscripcionActiva" to false,
                "error" to e.message
            )
        }
    }
}