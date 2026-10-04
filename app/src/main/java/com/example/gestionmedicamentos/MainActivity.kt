package com.example.gestionmedicamentos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.gestionmedicamentos.data.*
import com.example.gestionmedicamentos.ui.theme.GestionMedicamentosTheme
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var notification by mutableStateOf<Uri?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        notification = intent.data
        setContent { GestionMedicamentosTheme { MediVida(notification, clearNotification = { notification = null }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); notification = intent.data }
}

@Composable
fun MediVida(notification: Uri?, clearNotification: () -> Unit, vm: HealthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val tab = navController.currentBackStackEntryAsState().value?.destination?.route ?: "Inicio"
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var addMenu by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.user) { if (state.user == null) { form = null; editId = null } }
    if (!state.configured && state.user == null) {
        AuthBackground {
            HealthLogo(); Spacer(Modifier.height(20.dp)); Text("MediVida", fontSize = 32.sp, color = Color.White)
            Spacer(Modifier.height(24.dp))
            AuthCard {
                Text("Conecta tu proyecto Firebase", style = MaterialTheme.typography.headlineSmall)
                Text("Esta compilación todavía no está conectada a Firebase.")
                Text("1. Crea un proyecto Firebase y registra la app com.example.gestionmedicamentos.\n\n2. Activa Authentication por correo/contraseña y crea Firestore.\n\n3. Coloca google-services.json en la carpeta app, publica firestore.rules y vuelve a compilar.")
                Text("Consulta CONFIGURAR_FIREBASE.md, incluido en el proyecto.", color = PrimaryBlue)
                Spacer(Modifier.height(12.dp))
                Button(enabled = !state.loading, onClick = vm::enterDemo, modifier = Modifier.fillMaxWidth()) { Text("Probar la app sin cuenta") }
                Text("Modo local para pruebas. No introduzcas datos médicos reales: no sincroniza ni protege con una cuenta.", style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        }
    } else if (state.user == null) AuthScreen(state, vm)
    else {
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(30_000) } }
        val data = state.snapshot
        Scaffold(containerColor = BackgroundColor, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Inicio" to Icons.Default.Home, "Medicación" to Icons.Default.Medication, "Citas" to Icons.Default.CalendarMonth, "Nutrición" to Icons.Default.Restaurant, "Perfil" to Icons.Default.Person).forEach { (name, icon) ->
                    val accent = when (name) {
                        "Citas" -> AppointmentAccent
                        "Nutrición" -> NutritionAccent
                        "Medicación" -> MedicationAccent
                        else -> HomeAccent
                    }
                    NavigationBarItem(selected = tab == name, onClick = {
                        navController.navigate(name) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }, icon = { Icon(icon, name) }, label = { Text(name, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = accent, selectedTextColor = accent, indicatorColor = accent.copy(alpha = .12f)))
                }
            }
        }, floatingActionButton = { if (tab != "Perfil" && tab != "Acerca de") FloatingActionButton(onClick = { addMenu = true }, containerColor = PrimaryBlue, contentColor = Color.White) { Icon(Icons.Default.Add, "Agregar registro") } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.sync, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MutedText)
                NavHost(navController, startDestination = "Inicio", modifier = Modifier.weight(1f)) {
                    composable("Inicio") { TodayScreen(data, now, state.loading, vm, { navController.navigate(it) }) }
                    composable("Medicación") { RecordsScreen(data, { editId = it.id; form = it.kind.name }, now, state.loading, vm) }
                    composable("Citas") { Appointments(data, { editId = it.id; form = it.kind.name }) { addMenu = true } }
                    composable("Nutrición") { Nutrition(data, { editId = it.id; form = it.kind.name }) { addMenu = true } }
                    composable("Perfil") { ProfileScreen(data.profile, state.loading, vm, demo = !state.configured,
                        edit = { form = "PROFILE" }, about = { navController.navigate("Acerca de") }) }
                    composable("Acerca de") { AboutScreen(onBack = { navController.popBackStack() }) }
                }
            }
        }
        if (addMenu) AlertDialog(onDismissRequest = { addMenu = false }, title = { Text("¿Qué quieres agregar?") }, text = {
            Column { Kind.entries.forEach { kind -> TextButton(onClick = { editId = null; form = kind.name; addMenu = false }) { Text(kindLabel(kind)) } } }
        }, confirmButton = { TextButton(onClick = { addMenu = false }) { Text("Cerrar") } })
        form?.let { route ->
            if (route == "PROFILE") ProfileEditor(data.profile, state.loading, { form = null }, vm)
            else RecordEditor(Kind.valueOf(route), data.records.firstOrNull { it.id == editId }, state.loading, { form = null; editId = null }, vm)
        }
        val segments = notification?.pathSegments.orEmpty()
        if (segments.size == 4 && segments[0] == state.user) {
            var confirmingNotification by remember(notification) { mutableStateOf(false) }
            val record = data.records.firstOrNull { it.id == segments[1] && !it.archived }
            val task = record?.let { runCatching { Schedule.onDate(listOf(it), LocalDateTime.parse(segments[2]).toLocalDate()).firstOrNull { t -> t.key == segments[2] } }.getOrNull() }
            if (task != null && data.completions.none { it.recordId == task.record.id && it.occurrence == task.key }) {
                val appointmentReminder = segments[3].startsWith("pre")
                AlertDialog(onDismissRequest = clearNotification, title = { Text(if (appointmentReminder) "Tu cita se acerca" else "Es hora de ${task.record.title}") }, text = { Text("${task.record.detail}\n${task.dateTime.toLocalDate()} · ${task.dateTime.toLocalTime()}") },
                    confirmButton = { Button(enabled = !confirmingNotification && (appointmentReminder || !task.dateTime.isAfter(now)), onClick = {
                        if (appointmentReminder) {
                            clearNotification()
                        } else if (segments[3] == "snooze") {
                            vm.snooze(task)
                            clearNotification()
                        } else {
                            confirmingNotification = true
                            vm.complete(task, Status.TAKEN,
                                done = { confirmingNotification = false; clearNotification() },
                                failed = { confirmingNotification = false })
                        }
                    }) { Text(if (appointmentReminder) "Entendido" else if (confirmingNotification) "Guardando…" else if (segments[3] == "snooze") "Recordar en 10 min" else "Confirmar realizada") } },
                    dismissButton = { if (appointmentReminder) TextButton(onClick = clearNotification) { Text("Cerrar") }
                    else if (segments[3] == "snooze") TextButton(onClick = { vm.snooze(task); clearNotification() }) { Text("Posponer alarma") }
                    else TextButton(onClick = { vm.snooze(task); clearNotification() }) { Text("En 10 minutos") } })
            }
        }
    }
    state.error?.let { AlertDialog(onDismissRequest = vm::clearError, title = { Text("Revisa los datos") }, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("Entendido") } }) }
    state.message?.let { AlertDialog(onDismissRequest = vm::clearMessage, title = { Text("MediVida") }, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::clearMessage) { Text("Entendido") } }) }
}
