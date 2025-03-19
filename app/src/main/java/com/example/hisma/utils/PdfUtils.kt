package com.example.hisma.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.hisma.model.Lubricentro
import com.example.hisma.model.OilChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Envía un mensaje de texto por WhatsApp con los datos básicos del cambio de aceite.
 */
fun sendWhatsApp(oil: OilChange, context: Context) {
    val message = """
        *Cambio de Aceite*
        Vehículo: ${oil.dominio}
        Fecha: ${oil.fecha}
        KM: ${oil.km}
        Próx. KM: ${oil.proxKm}
    """.trimIndent()

    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("whatsapp://send?text=${Uri.encode(message)}")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Descarga un bitmap (logo) desde la URL (logoUrl).
 */
suspend fun fetchBitmapFromUrl(logoUrl: String): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(logoUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Comparte un PDF (File) por WhatsApp.
 */
fun sharePdfWithWhatsApp(pdfFile: File, context: Context) {
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
}

/**
 * Genera un PDF simple (sin logo) con datos básicos del OilChange.
 */
fun generateOilChangePdf(oilChange: OilChange, context: Context): File? {
    return try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 820, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 20f
            color = Color.BLACK
            isFakeBoldText = true
        }
        val normalPaint = Paint().apply {
            textSize = 14f
            color = Color.BLACK
        }

        canvas.drawText("Cambio de Aceite", 30f, 50f, titlePaint)
        canvas.drawText("Vehículo: ${oilChange.dominio}", 30f, 80f, normalPaint)
        canvas.drawText("Fecha: ${oilChange.fecha}", 30f, 100f, normalPaint)
        canvas.drawText("KM: ${oilChange.km}", 30f, 120f, normalPaint)
        canvas.drawText("Próx. KM: ${oilChange.proxKm}", 30f, 140f, normalPaint)

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "oil_change_${System.currentTimeMillis()}.pdf")
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()
        file
    } catch (e: Exception) {
        Log.e("PDF", "Error al generar PDF simple", e)
        null
    }
}

/**
 * Genera y comparte (por WhatsApp) el PDF simple (sin logo).
 */
fun pdfSharethroughWhatsApp(oil: OilChange, context: Context) {
    val pdfFile = generateOilChangePdf(oil, context)
    if (pdfFile != null) {
        sharePdfWithWhatsApp(pdfFile, context)
    } else {
        Toast.makeText(context, "Error generando PDF simple", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Genera el PDF simple y lo abre con un visor (en lugar de compartir).
 */
fun generatePdf(oil: OilChange, context: Context) {
    val pdfFile = generateOilChangePdf(oil, context)
    if (pdfFile != null) {
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
}

/**
 * Genera un PDF "fancy" con logo remoto y datos del Lubricentro + OilChange.
 */
fun generateFancyPdf(
    lubricentro: Lubricentro,
    oilChange: OilChange,
    context: Context,
    logoBitmap: Bitmap?
): File? {
    return try {
        val pdfDocument = PdfDocument()
        // Aquí se puede ajustar el tamaño de la página según tu diseño
        val pageInfo = PdfDocument.PageInfo.Builder(500, 800, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 20f
            color = Color.BLACK
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val titleH3 = Paint().apply {
            textSize = 18f
            color = Color.DKGRAY
            isFakeBoldText = true
        }
        val normalPaint = Paint().apply {
            textSize = 14f
            color = Color.BLACK
        }
        val smallPaint = Paint().apply {
            textSize = 12f
            color = Color.DKGRAY
        }
        val smallPaint_CENTER = Paint().apply {
            textSize = 12f
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }

        // Dibuja el logo si existe
        if (logoBitmap != null) {
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 100, 100, true)
            canvas.drawBitmap(scaledLogo, 30f, 30f, null)
        }

        // Datos del lubricentro
        var xPos = 150f
        var yPos = 50f
        canvas.drawText(lubricentro.nombreFantasia, xPos, yPos, titlePaint)
        yPos += 20f
        canvas.drawText("Responsable: ${lubricentro.responsable}", xPos, yPos, smallPaint)
        yPos += 15f
        canvas.drawText("Teléfono: ${lubricentro.telefono}", xPos, yPos, smallPaint)
        yPos += 15f
        canvas.drawText("Dirección: ${lubricentro.direccion}", xPos, yPos, smallPaint)
        yPos += 15f
        canvas.drawText("E-mail: ${lubricentro.email}", xPos, yPos, smallPaint)

        // Línea separadora
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 3f
        }
        canvas.drawLine(30f, 140f, 470f, 140f, linePaint)

        // Líneas verticales (ejemplos)
        val linePaint_B = Paint().apply {
            color = Color.BLUE
            strokeWidth = 5f
        }
        val linePaint_R = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
        }
        val linePaint_G = Paint().apply {
            color = Color.GREEN
            strokeWidth = 5f
        }
        val linePaint_Y = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 5f
        }


        // Ticket y fecha
        canvas.drawText("Ticket: ${oilChange.ticketNumber}", 30f, 160f, normalPaint)
        canvas.drawText("Fecha: ${oilChange.fecha}", 350f, 160f, normalPaint)

        // Recuadro para vehículo
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 3f
        }
        canvas.drawRect(30f, 180f, 470f, 250f, boxPaint)
        var dataY = 200f
        canvas.drawText("Dominio: ${oilChange.dominio}", 40f, dataY, titleH3)
        dataY += 20f
        canvas.drawText("KM actuales: ${oilChange.km}", 40f, dataY, normalPaint)
        dataY += 20f
        canvas.drawText("Próx Servicio: ${oilChange.proxKm}", 40f, dataY, normalPaint)





        // Sección ACEITE
        val aceiteY = 280f
        canvas.drawText("ACEITE", 30f, aceiteY, titleH3)
        canvas.drawText("Aceite: ${oilChange.aceite}", 40f, aceiteY + 20f, normalPaint)
        canvas.drawText("SAE: ${oilChange.sae}", 40f, aceiteY + 40f, normalPaint)
        canvas.drawText("Tipo: ${oilChange.tipo}", 40f, aceiteY + 60f, normalPaint)


        // Línea vertical para separar sección
        canvas.drawLine(20f, aceiteY-10f, 20f, aceiteY +60f, linePaint_B)



        // FILTROS
        var filtersY = aceiteY + 90f
        canvas.drawText("FILTROS", 30f, filtersY, titleH3)
        filtersY += 20f
        oilChange.filtros.forEach { (k, v) ->
            canvas.drawText("$k: $v", 40f, filtersY, normalPaint)
            filtersY += 20f
        }
        // Línea vertical para separar sección
        canvas.drawLine(20f, aceiteY + 80f , 20f, filtersY , linePaint_R)



        // EXTRAS
        var extrasY = filtersY + 30f
        canvas.drawText("EXTRAS", 30f, extrasY, titleH3)
        extrasY += 20f
        oilChange.extras.forEach { (k, v) ->
            canvas.drawText("$k: $v", 40f, extrasY, normalPaint)
            extrasY += 20f
        }

        // Línea vertical para separar sección
        canvas.drawLine(20f, filtersY + 20f, 20f, extrasY, linePaint_G)




        // Observaciones
        var obsY = extrasY + 30f
        canvas.drawText("OBSERVACIONES:", 30f, obsY, titleH3)
        obsY += 20f
        val obsLines = oilChange.observaciones.split("\n")
        obsLines.forEach { line ->
            canvas.drawText(line, 40f, obsY, normalPaint)
            obsY += 20f
        }
        // Línea vertical para separar sección
        canvas.drawLine(20f, extrasY+20f, 20f, obsY, linePaint_Y)




        canvas.drawLine(30f, obsY +20f, 470f, obsY +20f , linePaint)

        // Footer centrado: para centrar usa la mitad del ancho de la página (500/2 = 250)
        canvas.drawText("HISMA SERVICIOS - hisma.com.ar", 250f, obsY +40f , smallPaint_CENTER)

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "cambioAceite_${System.currentTimeMillis()}.pdf")
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()
        file
    } catch (e: Exception) {
        Log.e("PDF", "Error al generar PDF fancy", e)
        null
    }
}
