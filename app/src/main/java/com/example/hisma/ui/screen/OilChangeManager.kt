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

/**
 * Clase para manejar todas las operaciones relacionadas con los cambios de aceite
 */
class OilChangeManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    private val subscriptionManager = SubscriptionManager(db)

    /**
     * Verifica si hay una suscripción válida antes de realizar operaciones
     */
    suspend fun verificarSuscripcion(): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

            val isValid = subscriptionManager.checkCurrentSubscription(currentUser.uid)
            if (isValid) {
                Result.success(true)
            } else {
                Result.failure(Exception("Suscripción inválida o expirada"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando suscripción", e)
            Result.failure(e)
        }
    }

    /**
     * Carga todos los cambios de aceite del usuario actual
     */
    suspend fun loadOilChanges(): Result<List<OilChange>> {
        return try {
            // Primero verificamos que haya una suscripción válida
            val subscriptionResult = verificarSuscripcion()
            if (subscriptionResult.isFailure) {
                return Result.failure(subscriptionResult.exceptionOrNull()
                    ?: Exception("Error de suscripción"))
            }

            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

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
    suspend fun saveOilChange(oil: OilChange): Result<OilChange> {
        return try {
            // Verificamos suscripción antes de guardar
            if (oil.id.isBlank()) {  // Solo para nuevos cambios
                val subscriptionResult = verificarSuscripcion()
                if (subscriptionResult.isFailure) {
                    return Result.failure(subscriptionResult.exceptionOrNull()
                        ?: Exception("Error de suscripción"))
                }

                val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

                // Verificar que tenga cambios disponibles y decrementar
                val decremented = subscriptionManager.decrementAvailableChanges(currentUser.uid)
                if (!decremented) {
                    return Result.failure(Exception("No tienes cambios disponibles en tu suscripción"))
                }
            }

            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

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

    /**
     * Elimina un cambio de aceite
     */
    suspend fun deleteOilChange(oilId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Usuario no autenticado"))

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
     * Obtiene un URI para un archivo PDF utilizando FileProvider
     */
    private fun getPdfUri(pdfFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
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
                val uri = getPdfUri(pdfFile)
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
                val uri = getPdfUri(pdfFile)
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
            val message = "Hola ${contactName}! Te envío el detalle de tu cambio de aceite. ${lubricentroName} agradece tu confianza."

            // Formatear número de teléfono
            val phoneNumber = formatPhoneNumber(oil.contactCell)
            Log.d(TAG, "Enviando a número: $phoneNumber")

            // Conseguir URI del PDF
            val pdfUri = getPdfUri(pdfFile)

            // Intento 1: Enviar directamente con número específico y PDF adjunto
            val intentDirecto = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, message)
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
                whatsappIntent.component = ComponentName("com.whatsapp", "com.whatsapp.ContactPicker")
                whatsappIntent.putExtra("jid", "${phoneNumber}@s.whatsapp.net")
                whatsappIntent.putExtra(Intent.EXTRA_TEXT, message)
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
                sendIntent.putExtra(Intent.EXTRA_TEXT, message)
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
                val encodedMessage = URLEncoder.encode(message, "UTF-8")
                val whatsappUrl = "https://api.whatsapp.com/send?phone=$phoneNumber&text=$encodedMessage"
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