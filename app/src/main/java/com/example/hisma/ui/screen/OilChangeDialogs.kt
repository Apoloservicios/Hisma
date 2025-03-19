package com.example.hisma.ui.dialogs

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hisma.model.OilChange
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditOilChangeDialog(
    initialOilChange: OilChange?,
    suggestedTicketNumber: String? = null,
    onDismiss: () -> Unit,
    onSave: (OilChange) -> Unit
) {
    val context = LocalContext.current
    val isNew = (initialOilChange == null)

    // Campos principales
    var ticketNumber by remember { mutableStateOf(initialOilChange?.ticketNumber ?: suggestedTicketNumber ?: "") }
    var dominio by remember { mutableStateOf(initialOilChange?.dominio ?: "") }
    var fecha by remember { mutableStateOf(initialOilChange?.fecha ?: "") }
    var km by remember { mutableStateOf(initialOilChange?.km ?: "") }
    var proxKm by remember { mutableStateOf(initialOilChange?.proxKm ?: "") }
    var contactName by remember { mutableStateOf(initialOilChange?.contactName ?: "") }
    var contactCell by remember { mutableStateOf(initialOilChange?.contactCell ?: "") }
    var periodicity by remember { mutableStateOf(initialOilChange?.periodicity ?: "") }
    var proximaFecha by remember { mutableStateOf(initialOilChange?.proximaFecha ?: "") }
    var observaciones by remember { mutableStateOf(initialOilChange?.observaciones ?: "") }

    // ACEITE
    val aceiteOptions = listOf("CASTROL", "MOBIL", "SHELL", "VALVOLINE", "MOTUL", "TOTAL", "REPSOL", "YPF", "GULF", "ELF", "PETRONAS", "OTRA")
    var aceiteExpanded by remember { mutableStateOf(false) }
    var aceite by remember { mutableStateOf(initialOilChange?.aceite ?: aceiteOptions[0]) }
    var aceiteCustom by remember { mutableStateOf(initialOilChange?.aceiteCustom ?: "") }

    // SAE
    val saeOptions = listOf("SAE 30", "SAE 40", "SAE 50", "SAE 60", "0W-20", "0W-30", "0W-40", "5W-20", "5W-30", "5W-40", "5W-50", "10W-30", "10W-40", "15W-40", "20W-50", "OTRO")
    var saeExpanded by remember { mutableStateOf(false) }
    var sae by remember { mutableStateOf(initialOilChange?.sae ?: saeOptions[0]) }
    var saeCustom by remember { mutableStateOf(initialOilChange?.saeCustom ?: "") }

    // TIPO
    val tipoOptions = listOf("MINERAL", "SEMISINTÉTICO", "SINTÉTICO")
    var tipoExpanded by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf(initialOilChange?.tipo ?: tipoOptions[0]) }

    // Filtros
    val filtrosLabels = listOf("ACEITE", "AIRE", "COMBUSTIBLE", "HABITÁCULO")
    var filtrosChecked by remember {
        mutableStateOf(filtrosLabels.associateWith { initialOilChange?.filtros?.containsKey(it) == true })
    }
    var filtrosComments by remember {
        mutableStateOf(filtrosLabels.associateWith { initialOilChange?.filtros?.get(it) ?: "" })
    }

    // Extras
    val extrasLabels = listOf("ADITIVO", "DIFERENCIAL", "CAJA", "ENGRASE", "REFRIGERANTE")
    var extrasChecked by remember {
        mutableStateOf(extrasLabels.associateWith { initialOilChange?.extras?.containsKey(it) == true })
    }
    var extrasComments by remember {
        mutableStateOf(extrasLabels.associateWith { initialOilChange?.extras?.get(it) ?: "" })
    }

    // KM + Próx KM
    LaunchedEffect(km) {
        val kmVal = km.toIntOrNull() ?: 0
        proxKm = (kmVal + 10000).toString()
    }

    // Función para calcular la fecha del próximo cambio
    fun calcularProximaFecha() {
        if (fecha.isBlank() || periodicity.isBlank()) return

        try {
            val meses = periodicity.toIntOrNull() ?: 0
            if (meses <= 0) return

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val fechaActual = sdf.parse(fecha) ?: return

            val calendar = Calendar.getInstance()
            calendar.time = fechaActual
            calendar.add(Calendar.MONTH, meses)

            proximaFecha = sdf.format(calendar.time)
        } catch (e: Exception) {
            // Error al calcular fecha
        }
    }

    // Calcular próxima fecha cuando cambia la fecha o periodicidad
    LaunchedEffect(fecha, periodicity) {
        calcularProximaFecha()
    }

    // DatePickerDialog function
    fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val dateString = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                fecha = dateString
                calcularProximaFecha() // Recalcular al cambiar la fecha
            },
            year,
            month,
            day
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "NUEVO CAMBIO" else "EDITAR CAMBIO") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 600.dp)
                    .verticalScroll(rememberScrollState()) // SCROLL
            ) {
                // Número de ticket
                OutlinedTextField(
                    value = ticketNumber,
                    onValueChange = { ticketNumber = it },
                    label = { Text("Número de ticket (ej. L-00001)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // DOMINIO
                OutlinedTextField(
                    value = dominio,
                    onValueChange = { dominio = it.uppercase() },
                    label = { Text("DOMINIO (ABC123 / AB123CD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // FECHA con dos iconos
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = fecha,
                        onValueChange = {},
                        label = { Text("FECHA") },
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Icono para DatePicker
                    IconButton(onClick = { showDatePicker() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha", tint = Color(0xFF4CAF50))
                    }
                    // Icono para fecha actual
                    IconButton(onClick = {
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        fecha = sdf.format(Calendar.getInstance().time)
                    }) {
                        Icon(Icons.Default.Today, contentDescription = "Fecha actual", tint = Color(0xFF4CAF50))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // KM + Próx KM
                Row {
                    OutlinedTextField(
                        value = km,
                        onValueChange = { km = it.filter { c -> c.isDigit() } },
                        label = { Text("KM") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = proxKm,
                        onValueChange = { proxKm = it.filter { c -> c.isDigit() } },
                        label = { Text("PRÓX KM") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Nombre de contacto
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Nombre de Contacto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Número de celular
                OutlinedTextField(
                    value = contactCell,
                    onValueChange = { contactCell = it.filter { c -> c.isDigit() } },
                    label = { Text("Número de Celular") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Periodicidad (meses)
                OutlinedTextField(
                    value = periodicity,
                    onValueChange = {
                        periodicity = it.filter { c -> c.isDigit() }
                        calcularProximaFecha() // Recalcular al cambiar periodicidad
                    },
                    label = { Text("Periodicidad (meses)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Fecha próximo cambio (calculada automáticamente)
                OutlinedTextField(
                    value = proximaFecha,
                    onValueChange = {},
                    label = { Text("PRÓXIMO CAMBIO") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    enabled = false
                )
                Spacer(modifier = Modifier.height(8.dp))

                // ACEITE y SAE en la misma fila
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = if (aceite == "OTRA") "OTRA" else aceite.uppercase(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ACEITE") },
                            trailingIcon = {
                                IconButton(onClick = { aceiteExpanded = !aceiteExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = aceiteExpanded,
                            onDismissRequest = { aceiteExpanded = false }
                        ) {
                            aceiteOptions.forEach { option ->
                                DropdownMenuItem(
                                    onClick = {
                                        aceite = option
                                        aceiteExpanded = false
                                    },
                                    text = { Text(option) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = if (sae == "OTRO") "OTRO" else sae.uppercase(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("SAE") },
                            trailingIcon = {
                                IconButton(onClick = { saeExpanded = !saeExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = saeExpanded,
                            onDismissRequest = { saeExpanded = false }
                        ) {
                            saeOptions.forEach { option ->
                                DropdownMenuItem(
                                    onClick = {
                                        sae = option
                                        saeExpanded = false
                                    },
                                    text = { Text(option) }
                                )
                            }
                        }
                    }
                }
                if (aceite == "OTRA") {
                    OutlinedTextField(
                        value = aceiteCustom,
                        onValueChange = { aceiteCustom = it.uppercase() },
                        label = { Text("ACEITE (OTRA)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sae == "OTRO") {
                    OutlinedTextField(
                        value = saeCustom,
                        onValueChange = { saeCustom = it.uppercase() },
                        label = { Text("SAE (OTRO)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // TIPO
                Box {
                    OutlinedTextField(
                        value = tipo.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("TIPO") },
                        trailingIcon = {
                            IconButton(onClick = { tipoExpanded = !tipoExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = tipoExpanded,
                        onDismissRequest = { tipoExpanded = false }
                    ) {
                        tipoOptions.forEach { option ->
                            DropdownMenuItem(
                                onClick = {
                                    tipo = option
                                    tipoExpanded = false
                                },
                                text = { Text(option) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Filtros
                Text("FILTROS", style = MaterialTheme.typography.labelMedium)
                filtrosLabels.forEach { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val checked = filtrosChecked[label] ?: false
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                filtrosChecked = filtrosChecked.toMutableMap().apply { put(label, it) }
                            }
                        )
                        Text(label)
                        if (checked) {
                            OutlinedTextField(
                                value = filtrosComments[label] ?: "",
                                onValueChange = { newVal ->
                                    filtrosComments = filtrosComments.toMutableMap().apply { put(label, newVal.uppercase()) }
                                },
                                label = { Text("Comentario") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Extras
                Text("EXTRAS", style = MaterialTheme.typography.labelMedium)
                extrasLabels.forEach { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val checked = extrasChecked[label] ?: false
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                extrasChecked = extrasChecked.toMutableMap().apply { put(label, it) }
                            }
                        )
                        Text(label)
                        if (checked) {
                            OutlinedTextField(
                                value = extrasComments[label] ?: "",
                                onValueChange = { newVal ->
                                    extrasComments = extrasComments.toMutableMap().apply { put(label, newVal.uppercase()) }
                                },
                                label = { Text("Comentario") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Observaciones
                OutlinedTextField(
                    value = observaciones,
                    onValueChange = { observaciones = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validar campos obligatorios
                    if (dominio.isBlank() || fecha.isBlank() || km.isBlank() ||
                        proxKm.isBlank() || aceite.isBlank() || sae.isBlank() || tipo.isBlank() ||
                        ticketNumber.isBlank()
                    ) {
                        Toast.makeText(context, "Complete los campos obligatorios", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val filtrosList = filtrosLabels.filter { filtrosChecked[it] == true }
                        .associateWith { filtrosComments[it]?.uppercase() ?: "SI" }
                    val extrasList = extrasLabels.filter { extrasChecked[it] == true }
                        .associateWith { extrasComments[it]?.uppercase() ?: "SI" }

                    val newOil = OilChange(
                        id = initialOilChange?.id ?: "",
                        dominio = dominio,
                        fecha = fecha,
                        km = km,
                        proxKm = proxKm,
                        aceite = if (aceite == "OTRA") "" else aceite.uppercase(),
                        aceiteCustom = if (aceite == "OTRA") aceiteCustom.uppercase() else "",
                        sae = if (sae == "OTRO") "" else sae.uppercase(),
                        saeCustom = if (sae == "OTRO") saeCustom.uppercase() else "",
                        tipo = tipo.uppercase(),
                        extras = extrasList,
                        filtros = filtrosList,
                        observaciones = observaciones,
                        ticketNumber = ticketNumber,
                        contactName = contactName,
                        contactCell = contactCell,
                        periodicity = periodicity,
                        proximaFecha = proximaFecha
                    )
                    onSave(newOil)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("GUARDAR")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("CANCELAR")
            }
        }
    )
}