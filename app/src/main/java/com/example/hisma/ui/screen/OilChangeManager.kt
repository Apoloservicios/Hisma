package com.example.hisma.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.hisma.model.Lubricentro
import com.example.hisma.model.OilChange
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Clase para manejar todas las operaciones relacionadas con los cambios de aceite
 */
class OilChangeManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    /**
     * Verifica si el lubricentro tiene una suscripción activa con cambios disponibles
     */
    suspend fun verificarSuscripcion(): Result<Map<String, Any>> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

            // Obtener suscripciones activas
            val snapshot = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", currentUser.uid)
                .whereEqualTo("estado", "activa")
                .get()
                .await()

            if (snapshot.isEmpty) {
                return Result.failure(Exception("No hay suscripciones activas"))
            }

            val suscripciones = snapshot.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    val id = doc.id
                    val fechaFin = data["fechaFin"] as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now()
                    val cambiosRestantes = (data["cambiosRestantes"] as? Long)?.toInt() ?: 0
                    val isPaqueteAdicional = data["isPaqueteAdicional"] as? Boolean ?: false

                    arrayOf(id, fechaFin, cambiosRestantes, isPaqueteAdicional)
                } else null
            }

            // Verificar si hay suscripción principal activa y no vencida
            val suscripcionPrincipal = suscripciones.firstOrNull { !(it[3] as Boolean) }
            if (suscripcionPrincipal == null) {
                return Result.failure(Exception("No hay suscripción principal activa"))
            }

            val hoy = com.google.firebase.Timestamp.now()
            if ((suscripcionPrincipal[1] as com.google.firebase.Timestamp) < hoy) {
                return Result.failure(Exception("La suscripción está vencida"))
            }

            // Calcular cambios disponibles (principal + adicionales)
            val totalCambiosRestantes = suscripciones.sumOf { (it[2] as Int) }
            if (totalCambiosRestantes <= 0) {
                return Result.failure(Exception("No quedan cambios disponibles"))
            }

            // Calcular días restantes
            val millisHastaVencimiento = (suscripcionPrincipal[1] as com.google.firebase.Timestamp).toDate().time - hoy.toDate().time
            val diasRestantes = (millisHastaVencimiento / (1000 * 60 * 60 * 24)).toInt()

            // Devolver información de suscripción
            val result = mapOf(
                "suscripcionActiva" to true,
                "diasRestantes" to diasRestantes,
                "cambiosRestantes" to totalCambiosRestantes,
                "fechaVencimiento" to (suscripcionPrincipal[1] as com.google.firebase.Timestamp)
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registra el uso de un cambio (decrementa contador)
     */
    private suspend fun registrarUsoCambio() {
        try {
            val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")

            // Obtener suscripciones activas ordenadas: primero paquetes adicionales, luego principal
            val snapshot = db.collection("suscripciones")
                .whereEqualTo("lubricentroId", currentUser.uid)
                .whereEqualTo("estado", "activa")
                .get()
                .await()

            val suscripciones = snapshot.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    val id = doc.id
                    val cambiosRestantes = (data["cambiosRestantes"] as? Long)?.toInt() ?: 0
                    val isPaqueteAdicional = data["isPaqueteAdicional"] as? Boolean ?: false

                    Triple(id, cambiosRestantes, isPaqueteAdicional)
                } else null
            }

            // Primero usar paquetes adicionales
            val paqueteAdicional = suscripciones.firstOrNull { it.third && it.second > 0 }
            if (paqueteAdicional != null) {
                // Decrementar cambios en paquete adicional
                db.collection("suscripciones").document(paqueteAdicional.first)
                    .update(
                        mapOf(
                            "cambiosRealizados" to com.google.firebase.firestore.FieldValue.increment(1),
                            "cambiosRestantes" to com.google.firebase.firestore.FieldValue.increment(-1L)
                        )
                    ).await()
                return
            }

            // Si no hay paquetes adicionales, usar suscripción principal
            val suscripcionPrincipal = suscripciones.firstOrNull { !it.third && it.second > 0 }
            if (suscripcionPrincipal != null) {
                db.collection("suscripciones").document(suscripcionPrincipal.first)
                    .update(
                        mapOf(
                            "cambiosRealizados" to com.google.firebase.firestore.FieldValue.increment(1),
                            "cambiosRestantes" to com.google.firebase.firestore.FieldValue.increment(-1L)
                        )
                    ).await()
                return
            }

            throw Exception("No hay cambios disponibles")
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar uso de cambio", e)
            throw e
        }
    }
    /**
     * Carga todos los cambios de aceite del usuario actual
     */
    suspend fun loadOilChanges(): Result<List<OilChange>> {
        return try {
            val currentUser =
                auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

            val snapshot = db.collection("lubricentros")
                .document(currentUser.uid)
                .collection("cambiosAceite")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val list = snapshot.documents.map { doc ->
                doc.toObject(OilChange::class.java)!!.copy(id = doc.id)
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar lista de cambios de aceite", e)
            Result.failure(e)
        }
    }

    /**
     * Calcula el siguiente número de ticket basado en la lista actual
     */
    fun calculateNextTicketNumber(oilChanges: List<OilChange>): String {
        return if (oilChanges.isNotEmpty()) {
            val ticketNumbers = oilChanges.mapNotNull {
                it.ticketNumber.replace("L-", "").toIntOrNull()
            }
            val maxTicket = ticketNumbers.maxOrNull() ?: 0
            "L-" + String.format("%05d", maxTicket + 1)
        } else {
            "L-00001"
        }
    }

    /**
     * Guarda un cambio de aceite (nuevo o actualización)
     */
    /**
     * Guarda un cambio de aceite (nuevo o actualización)
     */
    suspend fun saveOilChange(oil: OilChange): Result<OilChange> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

            // Si es un nuevo cambio, verificar suscripción y decrementar contador
            if (oil.id.isBlank()) {
                // Verificar suscripción activa
                var isSubscriptionValid = false
                var isChangeRegistered = false

                val subscriptionManager = SubscriptionManager(context, auth, db)

                // Verificar suscripción activa (esperar resultado)
                var checkComplete = false
                subscriptionManager.checkActiveSubscription { isActive, subscription ->
                    isSubscriptionValid = isActive && (subscription?.availableChanges ?: 0) > 0
                    checkComplete = true
                }

                // Esperar a que se complete la verificación (simple polling)
                while (!checkComplete) {
                    kotlinx.coroutines.delay(50)
                }

                if (!isSubscriptionValid) {
                    return Result.failure(Exception("No hay una suscripción activa válida o no quedan cambios disponibles"))
                }

                // Registrar uso del cambio (esperar resultado)
                var registerComplete = false
                subscriptionManager.registerChangeUsage { success ->
                    isChangeRegistered = success
                    registerComplete = true
                }

                // Esperar a que se complete el registro
                while (!registerComplete) {
                    kotlinx.coroutines.delay(50)
                }

                if (!isChangeRegistered) {
                    return Result.failure(Exception("Error al registrar el uso del cambio"))
                }
            }

            val docRef = if (oil.id.isBlank()) {
                db.collection("lubricentros")
                    .document(currentUser.uid)
                    .collection("cambiosAceite")
                    .document()
            } else {
                db.collection("lubricentros")
                    .document(currentUser.uid)
                    .collection("cambiosAceite")
                    .document(oil.id)
            }

            docRef.set(oil).await()
            val savedOil = if (oil.id.isBlank()) {
                oil.copy(id = docRef.id)
            } else {
                oil
            }

            Result.success(savedOil)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar cambio de aceite", e)
            Result.failure(e)
        }
    }

    suspend fun deleteOilChange(oilId: String): Result<Unit> {
        return try {
            val currentUser =
                auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

            db.collection("lubricentros")
                .document(currentUser.uid)
                .collection("cambiosAceite")
                .document(oilId)
                .delete()
                .await()

            Toast.makeText(context, "Cambio eliminado", Toast.LENGTH_SHORT).show()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar cambio de aceite", e)
            Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
            Result.failure(e)
        }
    }

    /**
     * Obtiene el lubricentro actual
     */
    private suspend fun getCurrentLubricentro(): Lubricentro? {
        return try {
            val currentUser = auth.currentUser ?: return null

            val docLub = db.collection("lubricentros")
                .document(currentUser.uid)
                .get()
                .await()

            if (docLub.exists()) {
                docLub.toObject(Lubricentro::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener lubricentro", e)
            null
        }
    }

    /**
     * Obtiene los cambios de aceite próximos (dentro de los próximos 30 días)
     */
    suspend fun getProximosCambios(): Result<List<OilChange>> {
        return try {
            val allChanges = loadOilChanges().getOrDefault(emptyList())
            val currentDate = Calendar.getInstance().time
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            val proximosCambios = allChanges.filter { change ->
                if (change.proximaFecha.isBlank()) return@filter false

                try {
                    val proximaFecha = sdf.parse(change.proximaFecha) ?: return@filter false
                    val diffInMillis = proximaFecha.time - currentDate.time
                    val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

                    // Si está dentro de los próximos 30 días y no ha vencido
                    diffInDays in 0..30
                } catch (e: Exception) {
                    false
                }
            }.sortedBy { it.proximaFecha }

            Result.success(proximosCambios)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener próximos cambios", e)
            Result.failure(e)
        }
    }

    /**
     * Genera y abre un PDF con los datos del cambio de aceite
     */
    suspend fun openPdf(oil: OilChange) {
        try {
            val lub = getCurrentLubricentro() ?: return
            val logoBitmap = if (lub.logoUrl.isNotBlank()) {
                fetchBitmapFromUrl(lub.logoUrl)
            } else null

            val pdfFile = generateFancyPdf(lub, oil, context, logoBitmap)
            if (pdfFile != null) {
                // Abrir el PDF
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    pdfFile
                )
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(openIntent, "Abrir PDF"))
            } else {
                Toast.makeText(context, "Error generando PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir PDF", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Genera y comparte un PDF con los datos del cambio de aceite
     */
    suspend fun sharePdf(oil: OilChange) {
        try {
            val lub = getCurrentLubricentro() ?: return
            val logoBitmap = if (lub.logoUrl.isNotBlank()) {
                fetchBitmapFromUrl(lub.logoUrl)
            } else null

            val pdfFile = generateFancyPdf(lub, oil, context, logoBitmap)
            if (pdfFile != null) {
                // Compartir genéricamente
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    pdfFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir PDF"))
            } else {
                Toast.makeText(context, "Error generando PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al compartir PDF", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Genera un PDF y lo envía por WhatsApp al número de contacto
     * Con mensaje personalizado, intentando hacerlo en un solo paso.
     */
    suspend fun sendWhatsAppMessage(oil: OilChange) {
        if (oil.contactCell.isBlank()) {
            Toast.makeText(context, "No hay número de contacto", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val lub = getCurrentLubricentro() ?: return
            val logoBitmap = if (lub.logoUrl.isNotBlank()) {
                fetchBitmapFromUrl(lub.logoUrl)
            } else null

            // Generar el PDF
            val pdfFile = generateFancyPdf(lub, oil, context, logoBitmap)
            if (pdfFile == null) {
                Toast.makeText(context, "Error generando PDF", Toast.LENGTH_SHORT).show()
                return
            }

            // Preparar el mensaje
            val contactName = oil.contactName.takeIf { it.isNotBlank() } ?: "Cliente"
            val lubricentroName = lub.nombreFantasia.takeIf { it.isNotBlank() } ?: "Lubricentro"

            // Verificar si es recordatorio de próximo cambio
            val mensaje = if (oil.proximaFecha.isNotBlank()) {
                val currentDate = Calendar.getInstance().time
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                try {
                    val proximaFecha = sdf.parse(oil.proximaFecha)
                    if (proximaFecha != null) {
                        if (proximaFecha.before(currentDate)) {
                            // Cambio vencido
                            "Hola ${contactName}! Le recordamos que tenía programado un cambio de aceite para el ${oil.proximaFecha}. Por favor contáctenos para programar una nueva cita. ${lubricentroName} agradece su confianza."
                        } else {
                            // Próximo cambio
                            "Hola ${contactName}! Le recordamos que tiene programado un cambio de aceite para el ${oil.proximaFecha}. Puede contactarnos para agendar su cita. ${lubricentroName} agradece su confianza."
                        }
                    } else {
                        "Hola ${contactName}! Te envío el detalle de tu cambio de aceite. ${lubricentroName} agradece tu confianza."
                    }
                } catch (e: Exception) {
                    "Hola ${contactName}! Te envío el detalle de tu cambio de aceite. ${lubricentroName} agradece tu confianza."
                }
            } else {
                "Hola ${contactName}! Te envío el detalle de tu cambio de aceite. ${lubricentroName} agradece tu confianza."
            }

            // Formatear número de teléfono
            val phoneNumber = formatPhoneNumber(oil.contactCell)
            Log.d(TAG, "Enviando a número: $phoneNumber")

            // Conseguir URI del PDF
            val pdfUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                pdfFile
            )

            // Intento 1: Enviar directamente con número específico y PDF adjunto
            val intentDirecto = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, mensaje)
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra("jid", "${phoneNumber}@s.whatsapp.net") // Dirección específica de WhatsApp
                type = "application/pdf"
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                if (intentDirecto.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intentDirecto)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error método directo: ${e.message}")
                // Continuamos con el siguiente método
            }

            // Intento 2: Usar la característica de compartir de WhatsApp
            // con un intent personalizado
            try {
                // Esto intenta abrir WhatsApp y compartir directamente el PDF
                val whatsappIntent = Intent("android.intent.action.SEND")
                whatsappIntent.component =
                    ComponentName("com.whatsapp", "com.whatsapp.ContactPicker")
                whatsappIntent.putExtra("jid", "${phoneNumber}@s.whatsapp.net")
                whatsappIntent.putExtra(Intent.EXTRA_TEXT, mensaje)
                whatsappIntent.putExtra(Intent.EXTRA_STREAM, pdfUri)
                whatsappIntent.type = "application/pdf"
                whatsappIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                context.startActivity(whatsappIntent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error método ComponentName: ${e.message}")
                // Continuamos con el siguiente método
            }

            // Intento 3: Mostrar un selector de contactos de WhatsApp con el PDF adjunto
            try {
                val sendIntent = Intent("android.intent.action.SEND")
                sendIntent.component = ComponentName("com.whatsapp", "com.whatsapp.ContactPicker")
                sendIntent.putExtra(Intent.EXTRA_TEXT, mensaje)
                sendIntent.putExtra(Intent.EXTRA_STREAM, pdfUri)
                sendIntent.type = "application/pdf"
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                context.startActivity(sendIntent)

                // Mostramos un mensaje indicando que debe seleccionar el contacto
                Toast.makeText(
                    context,
                    "Por favor, selecciona el contacto ${oil.contactName}",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error método selector contactos: ${e.message}")
                // Última opción: hacerlo en dos pasos
            }

            // Última alternativa: Hacerlo en dos pasos
            // Primero abrir el chat con el contacto
            try {
                val encodedMessage = URLEncoder.encode(mensaje, "UTF-8")
                val whatsappUrl =
                    "https://api.whatsapp.com/send?phone=$phoneNumber&text=$encodedMessage"
                val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))

                if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(whatsappIntent)

                    Toast.makeText(
                        context,
                        "1. Envía este mensaje primero\n2. Luego compartiremos el PDF",
                        Toast.LENGTH_LONG
                    ).show()

                    // Esperamos unos segundos
                    kotlinx.coroutines.delay(3000)

                    // Compartir el PDF con WhatsApp
                    val pdfIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, pdfUri)
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    context.startActivity(pdfIntent)
                } else {
                    // Último recurso: solo compartir el PDF de forma genérica
                    sharePdfWithWhatsApp(pdfFile, context)
                }
            } catch (e: Exception) {
                // Si todo lo anterior falló, compartimos el PDF de forma genérica
                Log.e(TAG, "Todos los métodos fallaron: ${e.message}")
                sharePdfWithWhatsApp(pdfFile, context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error general: ${e.message}")
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Formatea un número de teléfono para WhatsApp API
     * Ejemplos:
     * - "11 1234 5678" -> "5491112345678" (Argentina)
     * - "1512345678" -> "5491512345678" (Argentina, con 15)
     */
    private fun formatPhoneNumber(phone: String): String {
        // Eliminar espacios, guiones, paréntesis y otros caracteres no numéricos
        val cleaned = phone.replace(Regex("[^0-9]"), "")

        Log.d(TAG, "Número original: $phone, limpio: $cleaned")

        // Formateo específico para Argentina
        return when {
            // Si ya tiene el formato internacional completo
            cleaned.startsWith("54") -> {
                // Verificar si ya tiene el 9 después del código de país
                if (cleaned.length >= 3 && cleaned[2] == '9') {
                    cleaned
                } else {
                    // Si el número empieza con 54 pero falta el 9 para WhatsApp
                    "54" + "9" + cleaned.substring(2)
                }
            }

            // Si empieza con 15 (prefijo móvil argentino)
            cleaned.startsWith("15") -> {
                // Asumimos que es de Buenos Aires (11) si solo tiene el 15
                if (cleaned.length <= 10) {
                    "5491" + cleaned.substring(2)
                } else {
                    // Si tiene más dígitos, asumimos que incluye el código de área
                    "549" + cleaned.substring(2)
                }
            }

            // Si empieza con el código de área (ej: 11)
            cleaned.length == 10 && cleaned.startsWith("11") -> {
                "549" + cleaned
            }

            // Si empieza con código de área pero sin el 9 requerido por WhatsApp
            cleaned.length == 10 -> {
                "549" + cleaned
            }

            // Si es un número corto, asumimos que es solo el número sin código de área
            cleaned.length <= 8 -> {
                "549" + "11" + cleaned // Asumimos Buenos Aires
            }

            // Para cualquier otro caso
            else -> {
                "549" + cleaned
            }
        }.also {
            Log.d(TAG, "Número formateado para WhatsApp: $it")
        }
    }

    companion object {
        private const val TAG = "OilChangeManager"
    }
}