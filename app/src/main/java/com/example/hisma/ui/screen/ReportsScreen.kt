package com.example.hisma.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.model.OilChange
import com.example.hisma.utils.OilChangeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val oilChangeManager = remember { OilChangeManager(context, auth, db, scope) }

    var isLoading by remember { mutableStateOf(true) }
    var oilChanges by remember { mutableStateOf(listOf<OilChange>()) }

    // Estado para las diferentes secciones del informe
    var proximosACambio by remember { mutableStateOf(listOf<OilChange>()) }
    var cambiosDelMes by remember { mutableStateOf(listOf<OilChange>()) }
    var cambiosVencidos by remember { mutableStateOf(listOf<OilChange>()) }

    // Estadísticas adicionales
    var totalCambios by remember { mutableStateOf(0) }
    var clientesRecurrentes by remember { mutableStateOf(0) }

    // Estado para la pestaña actual
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("PRÓXIMOS CAMBIOS", "CAMBIOS DEL MES", "CAMBIOS VENCIDOS")

    // Cargar datos al iniciar
    LaunchedEffect(Unit) {
        val result = oilChangeManager.loadOilChanges()
        if (result.isSuccess) {
            val allChanges = result.getOrDefault(emptyList())
            oilChanges = allChanges
            totalCambios = allChanges.size

            // Contar clientes recurrentes (dominios que aparecen más de una vez)
            val dominiosCount = allChanges.groupBy { it.dominio }
            clientesRecurrentes = dominiosCount.count { it.value.size > 1 }

            // Procesamiento de datos para informes
            val currentDate = Calendar.getInstance().time
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Obtener cambios próximos (dentro de los próximos 30 días)
            proximosACambio = allChanges.filter { change ->
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

            // Cambios realizados en el mes actual
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            cambiosDelMes = allChanges.filter { change ->
                try {
                    val fecha = sdf.parse(change.fecha) ?: return@filter false
                    calendar.time = fecha
                    calendar.get(Calendar.MONTH) == currentMonth &&
                            calendar.get(Calendar.YEAR) == currentYear
                } catch (e: Exception) {
                    false
                }
            }

            // Cambios vencidos (fecha próxima ya pasó)
            cambiosVencidos = allChanges.filter { change ->
                if (change.proximaFecha.isBlank()) return@filter false

                try {
                    val proximaFecha = sdf.parse(change.proximaFecha) ?: return@filter false
                    proximaFecha.before(currentDate)
                } catch (e: Exception) {
                    false
                }
            }.sortedBy { it.proximaFecha }

            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informes y Estadísticas") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Resumen de estadísticas generales
            if (!isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totalCambios.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Total Cambios")
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cambiosDelMes.size.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text("Este Mes")
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = clientesRecurrentes.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text("Clientes Rec.")
                        }
                    }
                }
            }

            // TabRow para las diferentes secciones
            TabRow(
                selectedTabIndex = selectedTabIndex
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            // Contenido según tab seleccionado
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTabIndex) {
                    0 -> ProximosCambiosList(proximosACambio, oilChangeManager)
                    1 -> CambiosDelMesList(cambiosDelMes)
                    2 -> CambiosVencidosList(cambiosVencidos, oilChangeManager)
                }
            }
        }
    }
}

@Composable
fun ProximosCambiosList(cambios: List<OilChange>, oilChangeManager: OilChangeManager) {
    val scope = rememberCoroutineScope()

    if (cambios.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay cambios próximos")
        }
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Vehículos con cambios programados en los próximos 30 días",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn {
                items(cambios) { cambio ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cambio.dominio,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = "Fecha próximo cambio",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF4CAF50)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Próximo cambio: ${cambio.proximaFecha}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Text(
                                        text = "Último cambio: ${cambio.fecha} - ${cambio.km} km",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    if (cambio.contactName.isNotBlank()) {
                                        Text(
                                            text = "Cliente: ${cambio.contactName}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                // Botones de acción
                                if (cambio.contactCell.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    oilChangeManager.sendWhatsAppMessage(cambio)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Send,
                                                contentDescription = "Enviar WhatsApp",
                                                tint = Color(0xFF25D366)
                                            )
                                        }

                                        Text(
                                            text = "Recordar vía WhatsApp",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CambiosDelMesList(cambios: List<OilChange>) {
    if (cambios.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay cambios registrados este mes")
        }
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Cambios realizados en el mes actual: ${cambios.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Mostrar estadísticas del mes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Estadísticas del mes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Conteo por tipo de aceite
                    val tipoAceiteCount = cambios.groupBy {
                        if (it.aceite.isBlank()) it.aceiteCustom else it.aceite
                    }

                    Text(
                        text = "Tipos de aceite más usados:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    tipoAceiteCount.entries.sortedByDescending { it.value.size }.take(3).forEach { (aceite, lista) ->
                        Text(
                            text = "• $aceite: ${lista.size} cambios",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Conteo por filtros cambiados
                    val filtrosCount = mutableMapOf<String, Int>()
                    cambios.forEach { cambio ->
                        cambio.filtros.keys.forEach { filtro ->
                            filtrosCount[filtro] = (filtrosCount[filtro] ?: 0) + 1
                        }
                    }

                    Text(
                        text = "Filtros cambiados:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    filtrosCount.entries.sortedByDescending { it.value }.forEach { (filtro, count) ->
                        Text(
                            text = "• $filtro: $count cambios",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Listado de cambios del mes",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn {
                items(cambios) { cambio ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cambio.dominio,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Fecha: ${cambio.fecha}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "KM: ${cambio.km}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                val aceiteDisplay = if (cambio.aceite.isBlank()) cambio.aceiteCustom else cambio.aceite
                                val saeDisplay = if (cambio.sae.isBlank()) cambio.saeCustom else cambio.sae

                                Text(
                                    text = aceiteDisplay,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = saeDisplay,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = cambio.tipo,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CambiosVencidosList(cambios: List<OilChange>, oilChangeManager: OilChangeManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (cambios.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay cambios vencidos")
        }
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Vehículos con cambios vencidos: ${cambios.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn {
                items(cambios) { cambio ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cambio.dominio,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = "Fecha vencida",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.Red
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Debió cambiar: ${cambio.proximaFecha}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Red
                                        )
                                    }

                                    Text(
                                        text = "Último cambio: ${cambio.fecha} - ${cambio.km} km",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    if (cambio.contactName.isNotBlank()) {
                                        Text(
                                            text = "Cliente: ${cambio.contactName}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                // Botones de acción
                                if (cambio.contactCell.isNotBlank()) {
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    oilChangeManager.sendWhatsAppMessage(cambio)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Send,
                                                contentDescription = "Enviar WhatsApp",
                                                tint = Color(0xFF25D366)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                // Implementar llamada telefónica
                                                val phoneNumber = cambio.contactCell
                                                if (phoneNumber.isNotBlank()) {
                                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                                        data = Uri.parse("tel:$phoneNumber")
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                contentDescription = "Llamar",
                                                tint = Color(0xFF2196F3)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}