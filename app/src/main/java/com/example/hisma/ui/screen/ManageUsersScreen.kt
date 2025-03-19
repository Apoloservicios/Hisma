package com.example.hisma.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement


data class Employee(
    val id: String = "",
    val nombre: String = "",
    val email: String = "",
    val rol: String = "empleado",
    val uidAuth: String = "",
    val estado: Boolean = true // true = activo, false = desactivado
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var employees by remember { mutableStateOf(listOf<Employee>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }

    // Cargar lista de empleados
    LaunchedEffect(Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                val snapshot = db.collection("lubricentros")
                    .document(currentUser.uid)
                    .collection("empleados")
                    .get()
                    .await()

                val list = snapshot.documents.map { doc ->
                    Employee(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        email = doc.getString("email") ?: "",
                        rol = doc.getString("rol") ?: "empleado",
                        uidAuth = doc.getString("uidAuth") ?: "",
                        estado = doc.getBoolean("estado") ?: true
                    )
                }
                employees = list
            } catch (e: Exception) {
                // Manejo de error si deseas
            } finally {
                isLoading = false
            }
        } else {
            // No hay usuario logueado
            navController.navigateUp()
        }
    }

    // Función para "Invitar" o agregar un empleado
    fun addEmployee(newEmployee: Employee) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            scope.launch {
                try {
                    val docRef = db.collection("lubricentros")
                        .document(currentUser.uid)
                        .collection("empleados")
                        .document() // genera un nuevo ID
                    val empData = mapOf(
                        "nombre" to newEmployee.nombre,
                        "email" to newEmployee.email,
                        "rol" to newEmployee.rol,
                        "uidAuth" to newEmployee.uidAuth,
                        "estado" to newEmployee.estado
                    )
                    docRef.set(empData).await()

                    // Actualizar lista local
                    employees = employees + newEmployee.copy(id = docRef.id)
                } catch (e: Exception) {
                    // Manejar error
                }
            }
        }
    }

    // Eliminar empleado
    fun deleteEmployee(empId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            scope.launch {
                try {
                    db.collection("lubricentros")
                        .document(currentUser.uid)
                        .collection("empleados")
                        .document(empId)
                        .delete()
                        .await()
                    employees = employees.filter { it.id != empId }
                } catch (e: Exception) {
                    // Manejar error
                }
            }
        }
    }

    // Editar empleado (incluyendo estado)
    fun editEmployee(updatedEmployee: Employee) {
        val currentUser = auth.currentUser
        if (currentUser != null && updatedEmployee.id.isNotBlank()) {
            scope.launch {
                try {
                    val empData = mapOf(
                        "nombre" to updatedEmployee.nombre,
                        "email" to updatedEmployee.email,
                        "rol" to updatedEmployee.rol,
                        "estado" to updatedEmployee.estado
                    )
                    db.collection("lubricentros")
                        .document(currentUser.uid)
                        .collection("empleados")
                        .document(updatedEmployee.id)
                        .update(empData)
                        .await()

                    employees = employees.map {
                        if (it.id == updatedEmployee.id) updatedEmployee else it
                    }
                } catch (e: Exception) {
                    // Manejar error
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Usuarios") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Text("<")
                    }
                }
            )
        },

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
                    .padding(16.dp)
            ) {
                Text(
                    text = "Lista de Empleados",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (employees.isEmpty()) {
                    Text("No hay empleados registrados")
                } else {
                    LazyColumn {
                        items(employees) { emp ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = emp.nombre, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = emp.email, style = MaterialTheme.typography.bodySmall)
                                        Text(text = if (emp.estado) "Activo" else "Desactivado", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            selectedEmployee = emp
                                            showEditDialog = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar"
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = {
                                            deleteEmployee(emp.id)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar"
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

    // Diálogo para agregar empleado
    if (showAddDialog) {
        AddEditEmployeeDialog(
            title = "Agregar Empleado",
            initialEmployee = Employee(),
            onDismiss = { showAddDialog = false },
            onSave = { newEmp ->
                addEmployee(newEmp)
                showAddDialog = false
            }
        )
    }

    // Diálogo para editar empleado
    if (showEditDialog && selectedEmployee != null) {
        AddEditEmployeeDialog(
            title = "Editar Empleado",
            initialEmployee = selectedEmployee!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedEmp ->
                editEmployee(updatedEmp)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun AddEditEmployeeDialog(
    title: String,
    initialEmployee: Employee,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit
) {
    var nombre by remember { mutableStateOf(initialEmployee.nombre) }
    var email by remember { mutableStateOf(initialEmployee.email) }
    var rol by remember { mutableStateOf(initialEmployee.rol) }
    var estado by remember { mutableStateOf(initialEmployee.estado) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = rol,
                    onValueChange = { rol = it },
                    label = { Text("Rol") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado: ")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = estado,
                        onCheckedChange = { estado = it }
                    )
                    Text(if (estado) "Activo" else "Desactivado")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = initialEmployee.copy(
                    nombre = nombre,
                    email = email,
                    rol = rol,
                    estado = estado
                )
                onSave(result)
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
