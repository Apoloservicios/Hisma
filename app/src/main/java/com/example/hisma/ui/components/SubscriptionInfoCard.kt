package com.example.hisma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hisma.model.Subscription
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SubscriptionInfoCard(subscription: Subscription?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Estado de Suscripción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (subscription != null) {
                // Determinar el color según el estado
                val isValid = subscription.isValid()
                val statusColor = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                val statusText = if (isValid) "Suscripción Activa" else "Suscripción Inactiva"

                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Formato de fecha
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaVencimiento = dateFormat.format(subscription.endDate.toDate())

                // Información de suscripción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cambios restantes:")
                        Text("Días restantes:")
                        Text("Vence el:")
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${subscription.availableChanges}",
                            fontWeight = FontWeight.Bold,
                            color = if (subscription.availableChanges <= 3) Color(0xFFF57C00) else Color.Unspecified
                        )
                        Text(
                            text = "${subscription.getDiasRestantes()}",
                            fontWeight = FontWeight.Bold,
                            color = if (subscription.getDiasRestantes() <= 3) Color(0xFFF57C00) else Color.Unspecified
                        )
                        Text(
                            text = fechaVencimiento,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Mostrar advertencia si está cerca de expirar
                if (subscription.availableChanges <= 3 || subscription.getDiasRestantes() <= 3) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3E0))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Advertencia",
                            tint = Color(0xFFF57C00)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tu suscripción está por vencer. Contacta a soporte para renovarla.",
                            color = Color(0xFFF57C00),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // No hay suscripción
                Text(
                    text = "Sin Suscripción Activa",
                    color = Color(0xFFF44336),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Contacta a soporte para activar una suscripción:",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "soporte@hisma.com.ar",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}