package com.example.hisma.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.hisma.model.Subscription
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SubscriptionExpiredDialog(
    subscription: Subscription?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Suscripción Inactiva",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (subscription != null) {
                    // Formato de fecha para mostrar
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val fechaVencimiento = dateFormat.format(subscription.endDate.toDate())

                    Text(
                        text = "Tu suscripción ha expirado o no tiene cambios disponibles:",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        SubscriptionInfoRow("Cambios restantes", subscription.availableChanges.toString())
                        SubscriptionInfoRow("Cambios utilizados", subscription.changesUsed.toString())
                        SubscriptionInfoRow("Fecha de vencimiento", fechaVencimiento)
                        SubscriptionInfoRow("Estado", if (subscription.active) "Activa" else "Inactiva")
                    }
                } else {
                    Text(
                        text = "No tienes una suscripción activa para este servicio.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Contacta con soporte para renovar tu suscripción:",
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

                Text(
                    text = "+54 9 11 1234-5678",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entendido")
                }
            }
        }
    }
}

@Composable
fun SubscriptionInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = if (
                (label == "Cambios restantes" && value.toIntOrNull() ?: 0 <= 0) ||
                (label == "Estado" && value == "Inactiva")
            ) MaterialTheme.colorScheme.error else Color.Unspecified
        )
    }
}