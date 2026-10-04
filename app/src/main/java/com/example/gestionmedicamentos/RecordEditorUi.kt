package com.example.gestionmedicamentos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.gestionmedicamentos.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordEditor(kind: Kind, existing: HealthRecord?, busy: Boolean, close: () -> Unit, vm: HealthViewModel) {
    val base = remember(existing?.id, kind) { existing ?: HealthRecord(kind = kind, title = "") }
    val initialTimes = remember(existing?.id, kind) {
        base.times.split(',').map { it.trim() }.filter { it.matches(Regex("\\d{2}:\\d{2}")) }.ifEmpty { listOf("08:00") }
    }
    val id by rememberSaveable(existing?.id, kind) { mutableStateOf(base.id) }
    var title by rememberSaveable(existing?.id, kind) { mutableStateOf(base.title) }
    var detail by rememberSaveable(existing?.id, kind) { mutableStateOf(base.detail) }
    var timeSlots by rememberSaveable(existing?.id, kind) { mutableStateOf(initialTimes) }
    var start by rememberSaveable(existing?.id, kind) { mutableStateOf(base.start) }
    var end by rememberSaveable(existing?.id, kind) { mutableStateOf(base.end) }
    var place by rememberSaveable(existing?.id, kind) { mutableStateOf(base.place) }
    var level by rememberSaveable(existing?.id, kind) { mutableStateOf(if (base.level == "Prohibido") "Restringido" else base.level) }
    var foodItems by rememberSaveable(existing?.id, kind) { mutableStateOf(base.foodItems) }
    var stockRemaining by rememberSaveable(existing?.id, kind) { mutableStateOf(base.stockRemaining?.toString().orEmpty()) }
    var unitsPerDose by rememberSaveable(existing?.id, kind) { mutableStateOf(base.unitsPerDose.toString()) }
    var stockAlertAt by rememberSaveable(existing?.id, kind) { mutableStateOf(base.stockAlertAt.toString()) }
    val reminderChoices = remember(base.reminderMinutes, base.dayBefore) {
        AppointmentReminderChoice.forCurrent(base.reminderMinutes, base.dayBefore)
    }
    var reminder by rememberSaveable(existing?.id, kind) {
        mutableStateOf(AppointmentReminderChoice.currentId(base.reminderMinutes, base.dayBefore))
    }
    var archive by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${if (existing == null) "Agregar" else "Editar"} ${kindLabel(kind).lowercase()}", style = MaterialTheme.typography.headlineSmall)
            if (kind == Kind.RESTRICTION) {
                Text("Categorías sugeridas", color = MutedText, style = MaterialTheme.typography.labelMedium)
                listOf("Verduras y legumbres", "Frutas", "Proteínas", "Cereales integrales", "Lácteos", "Grasas", "Azúcares y dulces", "Otros").chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { category -> FilterChip(selected = title == category, onClick = { title = category }, label = { Text(category) }) }
                    }
                }
            }
            Field(title, { title = it }, when (kind) { Kind.APPOINTMENT -> "Especialidad / médico"; Kind.RESTRICTION -> "Categoría de alimentos"; else -> "Nombre" })
            Field(detail, { detail = it }, when (kind) { Kind.MEDICINE -> "Dosis e instrucciones (ej. 1 tableta con comida)"; Kind.APPOINTMENT -> "Médico / notas"; Kind.RESTRICTION -> "Recomendación profesional (ej. priorizar en cada comida)"; else -> "Indicaciones de la comida" }, false)
            if (kind == Kind.RESTRICTION) Field(foodItems, { foodItems = it }, "Alimentos de esta categoría, separados por coma", false)
            if (kind != Kind.RESTRICTION) {
                RecordTimesField(kind, timeSlots, { timeSlots = it })
                DateField(start, { start = it }, if (kind == Kind.APPOINTMENT) "Fecha" else "Fecha de inicio", earliest = if (kind == Kind.APPOINTMENT) LocalDate.now() else null)
                if (kind != Kind.APPOINTMENT) DateField(end, { end = it }, "Fecha final (opcional)", true)
                if (kind == Kind.MEDICINE) {
                    Text("Control de stock", style = MaterialTheme.typography.titleMedium)
                    Field(stockRemaining, { stockRemaining = it }, "Unidades disponibles (opcional)", keyboardType = KeyboardType.Number)
                    Field(unitsPerDose, { unitsPerDose = it }, "Unidades por dosis", keyboardType = KeyboardType.Number)
                    Field(stockAlertAt, { stockAlertAt = it }, "Avisar cuando queden", keyboardType = KeyboardType.Number)
                }
            }
            if (kind == Kind.APPOINTMENT) {
                Field(place, { place = it }, "Lugar / consultorio")
                Text("Aviso previo", style = MaterialTheme.typography.titleMedium)
                reminderChoices.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { reminder = option.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reminder == option.id, { reminder = option.id })
                        Text(option.label)
                    }
                }
            }
            if (kind == Kind.RESTRICTION) {
                if (base.level == "Moderado") Text("Selecciona si esta categoría es Permitida o Restringida.", color = Color(0xFF9A5600), style = MaterialTheme.typography.bodySmall)
                listOf("Permitido", "Restringido").forEach { label -> Row(Modifier.fillMaxWidth().clickable { level = label }, verticalAlignment = Alignment.CenterVertically) { RadioButton(level == label, { level = label }); Text(label) } }
            }
            Button(enabled = !busy, onClick = {
                val selectedReminder = reminderChoices.first { it.id == reminder }
                vm.save(HealthRecord(id, kind, title.trim(), detail.trim(), timeSlots.joinToString(","), start, end, place.trim(), level,
                    if (kind == Kind.APPOINTMENT) selectedReminder.minutes else 0,
                    kind == Kind.APPOINTMENT && selectedReminder.dayBefore, base.archived,
                    if (kind == Kind.MEDICINE && stockRemaining.isNotBlank()) stockRemaining.toIntOrNull() ?: -1 else null,
                    if (kind == Kind.MEDICINE) unitsPerDose.toIntOrNull() ?: -1 else 1,
                    if (kind == Kind.MEDICINE) stockAlertAt.toIntOrNull() ?: -1 else 5,
                    if (kind == Kind.MEDICINE && stockRemaining.isNotBlank()) {
                        maxOf(base.stockTotal ?: base.stockRemaining ?: 0, stockRemaining.toIntOrNull() ?: -1)
                    } else null, if (kind == Kind.RESTRICTION) foodItems.trim() else base.foodItems), close)
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Guardando…" else "Guardar") }
            if (existing != null) TextButton(enabled = !busy, onClick = { archive = true }) { Text("Archivar registro", color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = close) { Text("Cancelar") }; Spacer(Modifier.height(24.dp))
        }
    }
    if (archive) AlertDialog(onDismissRequest = { archive = false }, title = { Text("Archivar ${kindLabel(kind).lowercase()}") }, text = { Text("Dejará de aparecer en la agenda y no generará más recordatorios. El historial se conserva.") }, confirmButton = { Button(enabled = !busy, onClick = { vm.delete(id) { archive = false; close() } }) { Text("Archivar") } }, dismissButton = { TextButton(onClick = { archive = false }) { Text("Cancelar") } })
}

@Composable
internal fun Field(value: String, change: (String) -> Unit, label: String, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
}
@Composable
private fun RecordTimesField(kind: Kind, times: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    if (kind == Kind.APPOINTMENT) {
        val time = times.firstOrNull() ?: "08:00"
        Text("Hora", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { openTimePicker(context, time) { onChange(listOf(it)) } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccessTime, null)
            Spacer(Modifier.width(8.dp))
            Text(time)
        }
    } else {
        val title = if (kind == Kind.MEDICINE) "Dosis al día" else "Horarios de comida"
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$title · ${times.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (times.size > 1) onChange(times.dropLast(1)) }, enabled = times.size > 1) { Icon(Icons.Default.Remove, "Quitar horario") }
            IconButton(onClick = {
                if (times.size < 24) {
                    val interval = if (kind == Kind.MEDICINE) 8L else 4L
                    val first = runCatching { LocalTime.parse(times.last()) }.getOrDefault(LocalTime.of(8, 0))
                    val next = (1..24).asSequence()
                        .map { first.plusHours(interval * it).toString().take(5) }
                        .firstOrNull { it !in times }
                    if (next != null) onChange(times + next)
                }
            }, enabled = times.size < 24) { Icon(Icons.Default.Add, "Agregar horario") }
        }
        times.forEachIndexed { index, time ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${if (kind == Kind.MEDICINE) "Dosis" else "Comida"} ${index + 1}", modifier = Modifier.weight(1f), color = MutedText)
                OutlinedButton(onClick = { openTimePicker(context, time) { selected -> onChange(times.toMutableList().also { it[index] = selected }) } }) {
                    Icon(Icons.Default.AccessTime, null)
                    Spacer(Modifier.width(8.dp))
                    Text(time)
                }
            }
        }
    }
}

private fun openTimePicker(context: android.content.Context, value: String, onSelected: (String) -> Unit) {
    val time = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(8, 0))
    android.app.TimePickerDialog(context, { _, hour, minute -> onSelected("%02d:%02d".format(hour, minute)) }, time.hour, time.minute, true).show()
}

private data class AppointmentReminderChoice(val id: String, val label: String, val minutes: Int, val dayBefore: Boolean) {
    companion object {
        private val standard = listOf(
            AppointmentReminderChoice("AT_TIME", "Solo a la hora", 0, false),
            AppointmentReminderChoice("ONE_HOUR", "1 hora antes", 60, false),
            AppointmentReminderChoice("ONE_DAY", "1 día antes", 0, true),
            AppointmentReminderChoice("ONE_DAY_AND_HOUR", "1 día y 1 hora antes", 60, true)
        )

        fun currentId(minutes: Int, dayBefore: Boolean) = standard.firstOrNull {
            it.minutes == minutes && it.dayBefore == dayBefore
        }?.id ?: "CURRENT"

        fun forCurrent(minutes: Int, dayBefore: Boolean): List<AppointmentReminderChoice> {
            val current = standard.firstOrNull { it.minutes == minutes && it.dayBefore == dayBefore }
            if (current != null) return standard
            val currentParts = buildList {
                if (dayBefore) add("1 día antes")
                if (minutes > 0) add("${formatReminderOffset(minutes)} antes")
            }
            return listOf(AppointmentReminderChoice("CURRENT", "Mantener aviso actual (${currentParts.joinToString(", ")})", minutes, dayBefore)) + standard
        }
    }
}

@Composable
internal fun DateField(value: String, change: (String) -> Unit, label: String, optional: Boolean = false, earliest: LocalDate? = null) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(earliest ?: LocalDate.now()).let { current ->
        if (earliest != null && current.isBefore(earliest)) earliest else current
    }
    val openPicker = {
        val picker = android.app.DatePickerDialog(context, { _, year, month, day -> change(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth)
        earliest?.let { picker.datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        picker.show()
    }
    OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth().clickable(onClick = openPicker), trailingIcon = {
        Row {
            if (optional && value.isNotEmpty()) IconButton(onClick = { change("") }) { Icon(Icons.Default.Close, "Quitar fecha final") }
            IconButton(onClick = openPicker) { Icon(Icons.Default.CalendarMonth, "Elegir fecha") }
        }
    })
}
