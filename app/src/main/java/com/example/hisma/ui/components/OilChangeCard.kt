package com.example.hisma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hisma.model.OilChange

@Composable
fun OilChangeCard(
    oilChange: OilChange,
    backgroundColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSendWhatsApp: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 2.dp
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(12.dp)
            ) {
                // Datos básicos
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${oilChange.ticketNumber} - VEHÍCULO: ${oilChange.dominio}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("FECHA: ${oilChange.fecha}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("KM: ${oilChange.km}  PRÓX: ${oilChange.proxKm}",
                        style = MaterialTheme.typography.bodyMedium)
                    if (oilChange.contactName.isNotBlank()) {
                        Text("CONTACTO: ${oilChange.contactName}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (oilChange.contactCell.isNotBlank()) {
                        Text("TELÉFONO: ${oilChange.contactCell}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (oilChange.periodicity.isNotBlank()) {
                        Text("PERIODICIDAD: ${oilChange.periodicity} meses",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Botones de acción en fila en la parte inferior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Editar
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = Color(0xFF2196F3))
                    }

                    // Generar PDF y abrir
                    IconButton(onClick = onOpenPdf) {
                        Icon(Icons.Default.PictureAsPdf,
                            contentDescription = "Abrir PDF",
                            tint = Color(0xFFFF9800))
                    }

                    // Compartir PDF
                    IconButton(onClick = onSharePdf) {
                        Icon(Icons.Default.Share,
                            contentDescription = "Compartir PDF",
                            tint = Color(0xFF4CAF50))
                    }

                    // Enviar a WhatsApp
                    IconButton(onClick = onSendWhatsApp) {
                        Icon(Icons.Default.Send,
                            contentDescription = "Enviar por WhatsApp",
                            tint = Color(0xFF25D366)) // Color de WhatsApp
                    }

                    // Eliminar
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = Color.Red)
                    }
                }
            }
        }
    }
}