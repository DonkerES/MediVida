package com.example.gestionmedicamentos

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.gestionmedicamentos.data.Profile

@Composable
internal fun ProfileScreen(profile: Profile, busy: Boolean, vm: HealthViewModel, demo: Boolean, edit: () -> Unit, about: () -> Unit) {
    val context = LocalContext.current
    var changePassword by remember { mutableStateOf(false) }
    var old by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAllowed = Reminders.get(context).exactAllowed()
    val fullScreenAllowed = Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
    LazyColumn(contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hero("Mi perfil", "Tu información de salud") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(54.dp).background(Color.White.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(profile.name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "MV" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(profile.name.ifBlank { "Completa tu perfil" }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (demo) "Modo de prueba local" else "Perfil personal", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                }
            }
        } }
        item { Box(Modifier.padding(horizontal = 16.dp)) { Panel {
            ProfileSectionTitle(Icons.Default.Person, "Datos personales", HomeAccent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric("EDAD", profile.age.ifBlank { "Sin registrar" }, Modifier.weight(1f), HomeAccent)
                ProfileMetric("PESO", if (profile.weight.isBlank()) "Sin registrar" else "${profile.weight} kg", Modifier.weight(1f), MedicationAccent)
            }
            ProfileMetric("GRUPO SANGUÍNEO", profile.blood.ifBlank { "Sin registrar" }, Modifier.fillMaxWidth(), AppointmentAccent)
            Column(Modifier.fillMaxWidth().background(Color(0xFFFFF5E8), RoundedCornerShape(10.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ALERGIAS", color = Color(0xFF9B5A0A), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(profile.allergies.ifBlank { "Ninguna registrada" }, color = DarkText, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Editar perfil") }
        } } }
        item { Box(Modifier.padding(horizontal = 16.dp)) { Panel {
            ProfileSectionTitle(Icons.Default.ContactPhone, "Contacto de emergencia", AppointmentAccent)
            Text(profile.contact.ifBlank { "Sin contacto registrado" }, color = DarkText, fontWeight = FontWeight.SemiBold)
            Text(profile.phone.ifBlank { "Agrega un teléfono para poder llamar rápidamente." }, color = MutedText, style = MaterialTheme.typography.bodySmall)
            Button(enabled = profile.phone.isNotBlank(), onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", profile.phone, null))) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppointmentAccent)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("Llamar al contacto") }
        } } }
        item { Box(Modifier.padding(horizontal = 16.dp)) { Panel {
            ProfileSectionTitle(Icons.Default.Alarm, "Alarmas y recordatorios", NutritionAccent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alarmas activadas", color = DarkText, fontWeight = FontWeight.SemiBold)
                    Text(if (profile.notifications) "Medicamentos, comidas y citas" else "No recibirás avisos", color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
                Switch(profile.notifications, { vm.profile(profile.copy(notifications = it)) {} }, enabled = !busy)
            }
            ProfileStatus("Notificaciones", if (notificationsAllowed) "Permitidas" else "Sin permiso", notificationsAllowed)
            ProfileStatus("Alarmas exactas", if (exactAllowed) "Permitidas" else "Pueden retrasarse", exactAllowed)
            if (Build.VERSION.SDK_INT >= 34) ProfileStatus("Pantalla completa", if (fullScreenAllowed) "Permitida" else "Requiere permiso", fullScreenAllowed)
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Permiso de notificaciones") }
            if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("Permitir alarmas exactas") }
            if (Build.VERSION.SDK_INT >= 34 && !fullScreenAllowed) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) }) { Text("Permitir alarma en pantalla completa") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, "health_alarm")) }) { Text("Tono, sonido y vibración") }
            Text("La puntualidad de las alarmas depende de los permisos y ajustes de tu teléfono.", style = MaterialTheme.typography.bodySmall, color = MutedText)
        } } }
        item { Box(Modifier.padding(horizontal = 16.dp)) { Panel {
            ProfileSectionTitle(Icons.Default.Security, "Seguridad y sincronización", HomeAccent)
            Text(if (demo) "Prueba local: los datos permanecen en este teléfono y no se sincronizan. No introduzcas información médica real." else "Tus datos se guardan en tu cuenta y hay una copia local para consultar sin conexión. Protege este teléfono con un bloqueo de pantalla.")
            if (!demo) TextButton(onClick = { changePassword = true }) { Text("Cambiar contraseña") }
            TextButton(onClick = about) { Text("Descripción de MediVida") }
            OutlinedButton(enabled = !busy, onClick = vm::logout) { Text(if (demo) "Salir del modo de prueba" else "Cambiar cuenta") }
            Text(if (demo) "Salir del modo de prueba desactiva los recordatorios. Los datos locales seguirán disponibles si vuelves a entrar." else "Al cambiar de cuenta se desactivan sus alarmas. Sus datos quedan guardados en esa cuenta; inicia sesión con otra para ver sus propios registros. Espera la sincronización antes de salir.", style = MaterialTheme.typography.bodySmall)
        } } }
    }
    if (changePassword) AlertDialog(onDismissRequest = { if (!busy) { changePassword = false; old = ""; replacement = "" } }, title = { Text("Cambiar contraseña") }, text = { Column {
        OutlinedTextField(old, { old = it }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(replacement, { replacement = it }, label = { Text("Nueva (mín. 12 caracteres)") }, visualTransformation = PasswordVisualTransformation())
    } }, confirmButton = { Button(enabled = !busy, onClick = { vm.password(old, replacement) { changePassword = false; old = ""; replacement = "" } }) { Text("Guardar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { changePassword = false; old = ""; replacement = "" }) { Text("Cancelar") } })
}

@Composable
private fun ProfileSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Text(title, color = DarkText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier, tint: Color) {
    Column(modifier.background(tint.copy(alpha = .07f), RoundedCornerShape(10.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = DarkText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileStatus(label: String, status: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (enabled) Color(0xFF159564) else Color(0xFFD18119), modifier = Modifier.size(18.dp))
        Text(label, Modifier.weight(1f).padding(start = 8.dp), color = DarkText, style = MaterialTheme.typography.bodySmall)
        Text(status, color = if (enabled) Color(0xFF159564) else Color(0xFFAD6812), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditor(profile: Profile, busy: Boolean, close: () -> Unit, vm: HealthViewModel) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var age by rememberSaveable { mutableStateOf(profile.age) }
    var weight by rememberSaveable { mutableStateOf(profile.weight) }
    var blood by rememberSaveable { mutableStateOf(profile.blood) }
    var allergies by rememberSaveable { mutableStateOf(profile.allergies) }
    var contact by rememberSaveable { mutableStateOf(profile.contact) }
    var phone by rememberSaveable { mutableStateOf(profile.phone) }
    ModalBottomSheet(onDismissRequest = { if (!busy) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Editar perfil", style = MaterialTheme.typography.headlineSmall)
            Field(name, { name = it }, "Nombre completo"); Field(age, { age = it }, "Edad")
            Field(weight, { weight = it }, "Peso (kg)"); Field(blood, { blood = it.uppercase() }, "Tipo de sangre (A+, O-, etc.)")
            Field(allergies, { allergies = it }, "Alergias conocidas", false)
            Field(contact, { contact = it }, "Contacto de emergencia"); Field(phone, { phone = it }, "Teléfono de emergencia")
            Button(enabled = !busy, onClick = { vm.profile(profile.copy(name = name.trim(), age = age, weight = weight, blood = blood, allergies = allergies, contact = contact, phone = phone), close) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar perfil") }
            TextButton(enabled = !busy, onClick = close) { Text("Cancelar") }; Spacer(Modifier.height(24.dp))
        }
    }
}
