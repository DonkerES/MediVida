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

private fun adherenceColor(percent: Int): Color = when {
    percent >= 100 -> Color(0xFF16A765)
    percent > 70 -> Color(0xFFF2C230)
    percent > 50 -> Color(0xFFF28C28)
    else -> Color(0xFFE5484D)
}

@Composable
internal fun TodayScreen(data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel, navigate: (String) -> Unit) {
    val allToday = Schedule.onDate(data.records, now.toLocalDate())
    val tasks = Schedule.pendingOnDate(data.records, now.toLocalDate(), data.completions)
    val medicationTasks = allToday.filter { it.record.kind == Kind.MEDICINE }
    val adherence = Schedule.adherence(medicationTasks, data.completions, now)
    val adherenceText = adherence?.let { "$it%" } ?: "—"
    val adherenceTint = adherence?.let(::adherenceColor) ?: MutedText
    val medicines = data.records.filter { it.kind == Kind.MEDICINE && !it.archived }
    val upcomingAppointments = data.records.filter { it.kind == Kind.APPOINTMENT && !it.archived }.mapNotNull { record ->
        runCatching { record to LocalDate.parse(record.start).atTime(Schedule.times(record.times).single()) }.getOrNull()
    }.filter { it.second >= now }.sortedBy { it.second }
    val nextAppointment = upcomingAppointments.firstOrNull()
    val appointmentsThisMonth = upcomingAppointments.count { it.second <= now.plusDays(30) }
    val stockWarnings = medicines.filter { medicine ->
        medicine.stockRemaining != null && medicine.stockRemaining <= medicine.stockAlertAt
    }.sortedBy { it.stockRemaining }
    var refillTarget by remember { mutableStateOf<HealthRecord?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { Hero("${greeting(now.hour)}, ${data.profile.name.substringBefore(' ')} 👋", now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es")))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryBox("${tasks.count { it.record.kind == Kind.MEDICINE }}", "dosis pendientes", Modifier.weight(1f))
                SummaryBox("$appointmentsThisMonth", "citas próximas", Modifier.weight(1f))
                SummaryBox(adherenceText, "adherencia", Modifier.weight(1f))
            }
        } }
        item { SectionHeading("Adherencia de hoy", "Ver todo →") { navigate("Medicación") } }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressCircle((adherence ?: 0) / 100f, adherenceTint, Color(0xFFE4EBF2), adherenceText, "HOY", MutedText)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val trackedToday = medicines.map { medicine ->
                                medicine to medicationTasks.filter { it.record.id == medicine.id && !it.dateTime.isAfter(now) }
                            }.filter { it.second.isNotEmpty() }
                            if (trackedToday.isEmpty()) Text(if (medicationTasks.isEmpty()) "No hay dosis programadas para hoy." else "Aún no hay dosis vencidas.", color = MutedText)
                            trackedToday.forEach { (medicine, doses) ->
                                val taken = doses.count { dose -> data.completions.any { it.recordId == dose.record.id && it.occurrence == dose.key && it.status == Status.TAKEN } }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(medicine.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("$taken/${doses.size}", style = MaterialTheme.typography.labelSmall, color = MutedText)
                                    }
                                    val medicineAdherence = taken * 100 / doses.size
                                    val medicineTint = adherenceColor(medicineAdherence)
                                    LinearProgressIndicator(progress = { taken.toFloat() / doses.size }, modifier = Modifier.fillMaxWidth().height(5.dp), color = medicineTint, trackColor = medicineTint.copy(alpha = .14f))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading("Próxima cita", "Ver agenda →") { navigate("Citas") } }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (nextAppointment == null) Empty("No tienes citas próximas registradas.")
                else {
                    val (record, dateTime) = nextAppointment
                    Panel {
                        Row(Modifier.fillMaxWidth().background(Color(0xFFEFFFF5), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.size(width = 54.dp, height = 58.dp).background(AppointmentAccent, RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(dateTime.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(dateTime.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(record.title, color = DarkText, fontWeight = FontWeight.Bold)
                                if (record.place.isNotBlank()) Text(record.place, color = MutedText, style = MaterialTheme.typography.bodySmall)
                                Text("${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${appointmentCountdown(now, dateTime)}", color = AppointmentAccent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading("Avisos de stock", "Gestionar →") { navigate("Medicación") } }
        if (stockWarnings.isEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Empty(if (medicines.any { it.stockRemaining != null }) "No hay medicamentos por debajo de su umbral." else "Configura el stock en un medicamento para activar los avisos.")
                }
            }
        } else {
            items(stockWarnings, key = { "stock-${it.id}" }) { medicine ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Panel {
                        Row(Modifier.fillMaxWidth().background(Color(0xFFFFF6E8), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFB86A00))
                            Column(Modifier.weight(1f)) {
                                Text(medicine.title, color = Color(0xFF8A4B00), fontWeight = FontWeight.SemiBold)
                                Text("Quedan ${medicine.stockRemaining} unidades", color = Color(0xFF8A4B00), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { refillTarget = medicine }) { Text("Reponer", color = Color(0xFF9A5600)) }
                        }
                    }
                }
            }
        }
    }
    refillTarget?.let { medicine ->
        StockRefillDialog(medicine, busy, onDismiss = { refillTarget = null }) { added ->
            vm.save(medicine.copy(stockRemaining = (medicine.stockRemaining ?: 0) + added, stockTotal = (medicine.stockTotal ?: medicine.stockRemaining ?: 0) + added)) { refillTarget = null }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(action, color = PrimaryBlue, style = MaterialTheme.typography.labelMedium) }
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun appointmentCountdown(now: LocalDateTime, appointment: LocalDateTime): String = when (val days = java.time.Duration.between(now, appointment).toDays()) {
    0L -> "hoy"
    1L -> "en 1 día"
    else -> "en $days días"
}

@Composable
private fun StockRefillDialog(record: HealthRecord, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var amount by remember(record.id) { mutableStateOf("") }
    val added = amount.toIntOrNull()
    val available = record.stockRemaining ?: 0
    val total = record.stockTotal ?: available
    val valid = added != null && added > 0 && available + added <= 100000 && total + added <= 100000
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reponer ${record.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Disponibles ahora: $available")
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(6) }, label = { Text("Unidades que agregas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { Button(enabled = !busy && valid, onClick = { added?.let(onSave) }) { Text(if (busy) "Guardando…" else "Guardar stock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
internal fun TaskCard(task: Task, data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel) {
    val completed = data.completions.firstOrNull { it.recordId == task.record.id && it.occurrence == task.key }
    val accent = accentFor(task.record.kind)
    var confirm by remember { mutableStateOf<Status?>(null) }
    Panel {
        Text("${task.dateTime.toLocalTime()} · ${kindLabel(task.record.kind)}", color = accent, fontWeight = FontWeight.Bold)
        Text(task.record.title, style = MaterialTheme.typography.titleLarge, color = DarkText)
        if (task.record.detail.isNotBlank()) Text(task.record.detail)
        if (task.record.kind == Kind.MEAL) {
            val restrictions = data.records.filter { it.kind == Kind.RESTRICTION && !it.archived && it.level in listOf("Restringido", "Prohibido") }
            if (restrictions.isNotEmpty()) Text("Evitar: ${restrictions.joinToString { it.title }}", color = Color(0xFFB3261E))
        }
        if (task.record.place.isNotBlank()) Text(task.record.place)
        if (completed != null) Text(if (completed.status == Status.TAKEN) "✓ Realizada" else "Omitida", fontWeight = FontWeight.Bold)
        else if (task.dateTime.isAfter(now)) Text("Programada", color = MutedText)
        else {
            Text("Pendiente de confirmar · ${task.dateTime.toLocalTime()}", color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { confirm = Status.TAKEN }) { Text(if (task.record.kind == Kind.MEDICINE) "Tomar ahora" else "Realizada") }
                TextButton(enabled = !busy, onClick = { confirm = Status.SKIPPED }) { Text("Omitir") }
            }
            TextButton(enabled = !busy, onClick = { vm.snooze(task) }) { Text("Recordar en 10 min") }
        }
    }
    confirm?.let { status -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (status == Status.TAKEN) "Confirmar registro" else "Confirmar omisión") }, text = { Text("${task.record.title} · ${task.dateTime.toLocalTime()}\nConfirma solo si corresponde a lo que hiciste; quedará guardado en el historial.") }, confirmButton = { Button(onClick = { vm.complete(task, status); confirm = null }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }) }
}
