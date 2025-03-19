package com.example.hisma.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hisma.model.OilChange
import com.example.hisma.ui.dialogs.AddEditOilChangeDialog
import com.example.hisma.ui.components.OilChangeCard
import com.example.hisma.utils.OilChangeManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OilChangesListScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val oilChangeManager = remember { OilChangeManager(context, auth, db, scope) }

    // Estados de la UI
    var searchQuery by remember { mutableStateOf("") }
    var oilChanges by remember { mutableStateOf(listOf<OilChange>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedChange by remember { mutableStateOf<OilChange?>(null) }
    var nextTicketNumber by remember { mutableStateOf("L-00001") }

    // Cargar la lista al iniciar
    LaunchedEffect(Unit) {
        val result = oilChangeManager.loadOilChanges()
        if (result.isSuccess) {
            oilChanges = result.getOrDefault(emptyList())
            nextTicketNumber = oilChangeManager.calculateNextTicketNumber(oilChanges)
        }
        isLoading = false
    }

    // Filtrar por dominio
    val filteredOilChanges = oilChanges.filter {
        it.dominio.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambios de Aceite") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                selectedChange = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(8.dp)
            ) {
                // Búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar por dominio") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Lista con itemsIndexed para alternar color
                LazyColumn {
                    itemsIndexed(filteredOilChanges) { index, oil ->
                        val backgroundColor = if (index % 2 == 0) Color(0xFFE3F2FD) else Color.White

                        OilChangeCard(
                            oilChange = oil,
                            backgroundColor = backgroundColor,
                            onEdit = {
                                selectedChange = oil
                                showDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    oilChangeManager.deleteOilChange(oil.id)
                                    oilChanges = oilChanges.filter { it.id != oil.id }
                                }
                            },
                            onOpenPdf = {
                                scope.launch {
                                    oilChangeManager.openPdf(oil)
                                }
                            },
                            onSharePdf = {
                                scope.launch {
                                    oilChangeManager.sharePdf(oil)
                                }
                            },
                            onSendWhatsApp = {
                                scope.launch {
                                    oilChangeManager.sendWhatsAppMessage(oil)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddEditOilChangeDialog(
            initialOilChange = selectedChange,
            suggestedTicketNumber = if (selectedChange == null) nextTicketNumber else null,
            onDismiss = { showDialog = false },
            onSave = { newOil ->
                val currentUser = auth.currentUser
                val userName = currentUser?.displayName ?: "Desconocido"
                val finalOil = if (newOil.id.isBlank()) {
                    newOil.copy(
                        createdAt = Timestamp.now(),
                        userName = userName
                    )
                } else {
                    newOil.copy(userName = userName)
                }

                scope.launch {
                    val result = oilChangeManager.saveOilChange(finalOil)
                    if (result.isSuccess) {
                        val savedOil = result.getOrNull()
                        if (savedOil != null) {
                            if (finalOil.id.isBlank()) {
                                // Es nuevo, lo agregamos al principio de la lista
                                oilChanges = listOf(savedOil) + oilChanges
                                nextTicketNumber = oilChangeManager.calculateNextTicketNumber(oilChanges)
                            } else {
                                // Actualizamos la existente
                                oilChanges = oilChanges.map { old ->
                                    if (old.id == savedOil.id) savedOil else old
                                }
                            }
                        }
                    }else {
                        // AÑADE ESTO:
                        val error = result.exceptionOrNull()?.message ?: "Error desconocido al guardar"
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        Log.e("OilChangesListScreen", "Error al guardar: $error")
                    }



                }
                showDialog = false
            }
        )
    }
}