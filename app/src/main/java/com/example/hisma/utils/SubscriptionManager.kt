package com.example.hisma.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.hisma.model.Subscription
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class SubscriptionManager(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "SubscriptionManager"

    // Método actualizado para verificar suscripción con callback simple
    fun checkActiveSubscription(callback: (Boolean, Subscription?) -> Unit) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                // Consultar suscripciones activas
                val snapshot = db.collection("suscripciones")
                    .whereEqualTo("lubricentroId", currentUser.uid)
                    .whereEqualTo("estado", "activa")
                    .get()
                    .await()

                if (snapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        callback(false, null)
                    }
                    return@launch
                }

                // Mapear documentos a objetos Subscription
                val subscriptions = snapshot.documents.mapNotNull { doc ->
                    try {
                        Subscription(
                            id = doc.id,
                            lubricentroId = doc.getString("lubricentroId") ?: "",
                            planId = doc.getString("planId") ?: "",
                            startDate = doc.getTimestamp("fechaInicio") ?: Timestamp.now(),
                            endDate = doc.getTimestamp("fechaFin") ?: Timestamp.now(),
                            active = doc.getString("estado") == "activa",
                            valid = true,
                            totalChangesAllowed = doc.getLong("cambiosTotales")?.toInt() ?: 0,
                            changesUsed = doc.getLong("cambiosRealizados")?.toInt() ?: 0,
                            availableChanges = doc.getLong("cambiosRestantes")?.toInt() ?: 0,
                            isPaqueteAdicional = doc.getBoolean("isPaqueteAdicional") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val validSubscriptions = subscriptions.filter { sub ->
                    val now = Timestamp.now()
                    sub.active && sub.endDate > now && sub.availableChanges > 0
                }

                withContext(Dispatchers.Main) {
                    if (validSubscriptions.isEmpty()) {
                        callback(false, null)
                    } else {
                        val principalSub = validSubscriptions.firstOrNull { !it.isPaqueteAdicional }
                        callback(true, principalSub ?: validSubscriptions.first())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando suscripción: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false, null)
                }
            }
        }
    }

    // Obtener todas las suscripciones
    fun getAllSubscriptions(callback: (List<Subscription>) -> Unit) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                val snapshot = db.collection("suscripciones")
                    .whereEqualTo("lubricentroId", currentUser.uid)
                    .get()
                    .await()

                val subscriptions = snapshot.documents.mapNotNull { doc ->
                    try {
                        Subscription(
                            id = doc.id,
                            lubricentroId = doc.getString("lubricentroId") ?: "",
                            planId = doc.getString("planId") ?: "",
                            startDate = doc.getTimestamp("fechaInicio") ?: Timestamp.now(),
                            endDate = doc.getTimestamp("fechaFin") ?: Timestamp.now(),
                            active = doc.getString("estado") == "activa",
                            valid = true,
                            totalChangesAllowed = doc.getLong("cambiosTotales")?.toInt() ?: 0,
                            changesUsed = doc.getLong("cambiosRealizados")?.toInt() ?: 0,
                            availableChanges = doc.getLong("cambiosRestantes")?.toInt() ?: 0,
                            isPaqueteAdicional = doc.getBoolean("isPaqueteAdicional") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    callback(subscriptions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo suscripciones: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    // Método para registrar uso de cambio
    fun registerChangeUsage(callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

                val snapshot = db.collection("suscripciones")
                    .whereEqualTo("lubricentroId", currentUser.uid)
                    .whereEqualTo("estado", "activa")
                    .get()
                    .await()

                if (snapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        callback(false)
                    }
                    return@launch
                }

                // Buscar suscripciones con cambios disponibles
                val validDocs = snapshot.documents.filter { doc ->
                    val fechaFin = doc.getTimestamp("fechaFin")
                    val cambiosRestantes = doc.getLong("cambiosRestantes")?.toInt() ?: 0

                    fechaFin != null && fechaFin > Timestamp.now() && cambiosRestantes > 0
                }

                if (validDocs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        callback(false)
                    }
                    return@launch
                }

                // Primero usar paquetes adicionales
                val docsOrdenados = validDocs.sortedBy {
                    val isPaquete = it.getBoolean("isPaqueteAdicional") ?: false
                    if (isPaquete) 0 else 1
                }

                // Actualizar la primera suscripción con cambios disponibles
                val docToUpdate = docsOrdenados.first()
                db.collection("suscripciones").document(docToUpdate.id)
                    .update(
                        mapOf(
                            "cambiosRealizados" to FieldValue.increment(1),
                            "cambiosRestantes" to FieldValue.increment(-1L),
                            "updatedAt" to Timestamp.now()
                        )
                    ).await()

                withContext(Dispatchers.Main) {
                    callback(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar uso de cambio: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    // Crear solicitud de suscripción
    fun requestNewSubscription(
        tipoSuscripcion: String,
        isPaqueteAdicional: Boolean,
        callback: (Boolean) -> Unit
    ) {
        scope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

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
                    callback(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al solicitar suscripción: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    // Activar período de prueba
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

                // Crear suscripción de prueba
                val startDate = Timestamp.now()
                val calendar = Calendar.getInstance()
                calendar.time = startDate.toDate()
                calendar.add(Calendar.DAY_OF_MONTH, 30)
                val endDate = Timestamp(calendar.time)

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

                db.collection("suscripciones").document().set(suscripcionData).await()

                // Marcar que el usuario ha usado su prueba
                db.collection("lubricentros").document(currentUser.uid)
                    .update("trialUsed", true)
                    .await()

                withContext(Dispatchers.Main) {
                    callback(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al activar prueba: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    // Crear prueba para un nuevo usuario
    suspend fun createTrial(lubricentroId: String): Boolean {
        return try {
            // Verificar si ya tiene suscripciones
            val existingSubscriptions = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", lubricentroId)
                .limit(1)
                .get()
                .await()

            if (!existingSubscriptions.isEmpty) {
                return false
            }

            // Crear suscripción de prueba
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

            db.collection("suscripciones").document().set(suscripcionData).await()

            // Intentar marcar el trial como usado
            try {
                db.collection("lubricentros").document(lubricentroId)
                    .update("trialUsed", true)
                    .await()
            } catch (e: Exception) {
                // Ignorar errores, posiblemente el campo no existe
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear suscripción de prueba: ${e.message}", e)
            false
        }
    }
}