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

@Composable
internal fun RecordsScreen(data: Snapshot, edit: (HealthRecord) -> Unit, now: LocalDateTime, busy: Boolean, vm: HealthViewModel) {
    var section by rememberSaveable { mutableStateOf("HOY") }
    var historyMonthText by rememberSaveable { mutableStateOf(YearMonth.from(now).toString()) }
    var selectedHistoryDate by rememberSaveable { mutableStateOf(now.toLocalDate().toString()) }
    var confirmingDose by remember { mutableStateOf<Task?>(null) }
    var savingDose by remember { mutableStateOf(false) }
    val records = data.records.filter { it.kind == Kind.MEDICINE }.sortedBy { it.title.lowercase() }
    val activeRecords = records.filter { !it.archived }
    val todayTasks = Schedule.onDate(activeRecords, now.toLocalDate())
    val dueToday = todayTasks.filter { !it.dateTime.isAfter(now) }
    val todayDoses = dueToday.size
    val takenToday = dueToday.count { task -> data.completions.any { it.recordId == task.record.id && it.occurrence == task.key && it.status == Status.TAKEN } }
    val adherence = if (todayDoses == 0) 0f else takenToday.toFloat() / todayDoses
    val context = LocalContext.current
    val installDate = remember(context) {
        runCatching {
            Instant.ofEpochMilli(context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime)
                .atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrDefault(now.toLocalDate())
    }
    val completionDates = data.completions.filter { completion -> records.any { it.id == completion.recordId } }
        .mapNotNull { runCatching { LocalDateTime.parse(it.occurrence).toLocalDate() }.getOrNull() }
    val firstHistoryDate = minOf(installDate, completionDates.minOrNull() ?: installDate)
    val historyMonth = YearMonth.parse(historyMonthText)
    val selectedDate = runCatching { LocalDate.parse(selectedHistoryDate) }.getOrDefault(now.toLocalDate())
    val datedCompletions = data.completions.filter { completion ->
        records.any { it.id == completion.recordId } && runCatching { LocalDateTime.parse(completion.occurrence).toLocalDate() == selectedDate }.getOrDefault(false)
    }
    val savedTasks = datedCompletions.mapNotNull { completion ->
        val record = records.firstOrNull { it.id == completion.recordId } ?: return@mapNotNull null
        val dateTime = runCatching { LocalDateTime.parse(completion.occurrence) }.getOrNull() ?: return@mapNotNull null
        Task(record, dateTime)
    }
    val dayTasks = (Schedule.onDate(records, selectedDate, includeArchived = true) + savedTasks)
        .distinctBy { it.record.id + it.key }.sortedBy { it.dateTime }

    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Hero("Mis medicamentos", "Tu tratamiento, organizado", MedicationGradient) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ProgressCircle(adherence, Color.White, Color.White.copy(alpha = .25f), if (todayDoses == 0) "—" else "${(adherence * 100).toInt()}%", "HOY", Color.White.copy(alpha = .9f))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Adherencia de hoy", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                        Text(if (todayDoses == 0) "Aún no hay dosis vencidas" else "$takenToday de $todayDoses dosis vencidas completadas", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("HOY" to "Pauta de hoy", "HISTORIAL" to "Historial", "INVENTARIO" to "Mi inventario").forEach { (id, label) ->
                    FilterChip(selected = section == id, onClick = { section = id }, label = { Text(label) })
                }
            }
        }
        when (section) {
            "HOY" -> {
                item { Text("PAUTA DEL DÍA", Modifier.padding(horizontal = 16.dp), color = MutedText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                val todaysMedicines = activeRecords.map { medicine -> medicine to todayTasks.filter { it.record.id == medicine.id } }.filter { it.second.isNotEmpty() }
                if (todaysMedicines.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { Empty(if (activeRecords.isEmpty()) "Agrega tu primer medicamento con el botón +." else "No tienes dosis programadas para hoy.") } }
                items(todaysMedicines, key = { it.first.id }) { (medicine, doses) ->
                    val color = MedicationCardAccents[activeRecords.indexOf(medicine) % MedicationCardAccents.size]
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        MedicationDoseCard(medicine, doses, data.completions, now, color) { confirmingDose = it }
                    }
                }
            }
            "HISTORIAL" -> {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Panel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(enabled = historyMonth > YearMonth.from(firstHistoryDate), onClick = { historyMonthText = historyMonth.minusMonths(1).toString() }) { Icon(Icons.Default.ChevronLeft, "Mes anterior") }
                                Text(historyMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, fontWeight = FontWeight.Bold)
                                IconButton(enabled = historyMonth < YearMonth.from(now), onClick = { historyMonthText = historyMonth.plusMonths(1).toString() }) { Icon(Icons.Default.ChevronRight, "Mes siguiente") }
                            }
                            Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), color = MutedText, style = MaterialTheme.typography.labelMedium) } }
                            val offset = historyMonth.atDay(1).dayOfWeek.value - 1
                            val weeks = (offset + historyMonth.lengthOfMonth() + 6) / 7
                            (0 until weeks).forEach { week ->
                                Row {
                                    (0..6).forEach { column ->
                                        val day = week * 7 + column - offset + 1
                                        Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                                            if (day in 1..historyMonth.lengthOfMonth()) {
                                                val date = historyMonth.atDay(day)
                                                val enabled = date >= firstHistoryDate && !date.isAfter(now.toLocalDate())
                                                val hasEntry = completionDates.contains(date)
                                                val chosen = selectedDate == date
                                                Column(Modifier.fillMaxWidth().height(40.dp).background(if (chosen) MedicationAccent.copy(alpha = .14f) else Color.Transparent, CircleShape)
                                                    .clickable(enabled = enabled) { selectedHistoryDate = date.toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                    Text(day.toString(), color = if (!enabled) MutedText.copy(alpha = .38f) else if (chosen) MedicationAccent else DarkText, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                                                    Text(if (hasEntry) "•" else " ", color = MedicationAccent, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Text("Elige un día para ver sus dosis registradas y pendientes.", style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                    }
                }
                item { Text("${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (dayTasks.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { Empty("No hay dosis registradas para este día.") } }
                items(dayTasks, key = { "history-${it.record.id}-${it.key}" }) { task ->
                    val color = MedicationCardAccents[records.indexOfFirst { it.id == task.record.id }.coerceAtLeast(0) % MedicationCardAccents.size]
                    val completion = data.completions.firstOrNull { it.recordId == task.record.id && it.occurrence == task.key }
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        HistoryDoseCard(task, completion, color, now) { if (completion == null && !task.record.archived && !task.dateTime.isAfter(now)) confirmingDose = task }
                    }
                }
            }
            else -> {
                item { Text("TRATAMIENTOS Y EXISTENCIAS", Modifier.padding(horizontal = 16.dp), color = MutedText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                if (activeRecords.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { Empty("Tu inventario está vacío. Usa + para registrar un medicamento.") } }
                items(activeRecords, key = { "inventory-${it.id}" }) { medicine ->
                    val color = MedicationCardAccents[activeRecords.indexOf(medicine) % MedicationCardAccents.size]
                    Box(Modifier.padding(horizontal = 16.dp)) { RecordCard(medicine, edit, color) }
                }
            }
        }
    }
    confirmingDose?.let { task ->
        AlertDialog(onDismissRequest = { if (!savingDose) confirmingDose = null }, title = { Text("Registrar dosis") },
            text = { Text("${task.record.title} · ${task.dateTime.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM · HH:mm", Locale.forLanguageTag("es")))}\nConfirma solo si tomaste esta dosis.") },
            confirmButton = { Button(enabled = !savingDose, onClick = {
                savingDose = true
                vm.complete(task, Status.TAKEN, done = { savingDose = false; confirmingDose = null }, failed = { savingDose = false })
            }) { Text(if (savingDose) "Guardando…" else "Marcar como tomada") } },
            dismissButton = { TextButton(enabled = !savingDose, onClick = {
                savingDose = true
                vm.complete(task, Status.SKIPPED, done = { savingDose = false; confirmingDose = null }, failed = { savingDose = false })
            }) { Text("Omitir dosis") } })
    }
}

@Composable
private fun MedicationDoseCard(medicine: HealthRecord, doses: List<Task>, completions: List<Completion>, now: LocalDateTime, accent: Color, onDoseClick: (Task) -> Unit) {
    Card(Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = .2f), RoundedCornerShape(14.dp)), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(medicine.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.Bold)
                if (medicine.stockRemaining != null) Text("${medicine.stockRemaining}/${medicine.stockTotal ?: medicine.stockRemaining}", color = if (medicine.stockRemaining <= medicine.stockAlertAt) Color(0xFFB86A00) else accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (medicine.detail.isNotBlank()) Text(medicine.detail, color = MutedText, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                doses.forEach { task ->
                    val completion = completions.firstOrNull { it.recordId == task.record.id && it.occurrence == task.key }
                    val taken = completion?.status == Status.TAKEN
                    val skipped = completion?.status == Status.SKIPPED
                    val enabled = completion == null && !task.dateTime.isAfter(now)
                    val chipColor = if (taken) accent.copy(alpha = .13f) else Color(0xFFF1F4F8)
                    val labelColor = if (taken) accent else MutedText
                    Row(Modifier.border(1.dp, if (taken) accent.copy(alpha = .35f) else Color(0xFFDCE4EC), RoundedCornerShape(10.dp))
                        .background(chipColor, RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { onDoseClick(task) }
                        .padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (taken) "✓" else if (skipped) "−" else "○", color = labelColor, fontWeight = FontWeight.Bold)
                        Text(task.dateTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = labelColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (medicine.stockRemaining != null) {
                val total = (medicine.stockTotal ?: medicine.stockRemaining).coerceAtLeast(1)
                LinearProgressIndicator(progress = { medicine.stockRemaining.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(5.dp), color = accent, trackColor = accent.copy(alpha = .14f))
            }
        }
    }
}

@Composable
private fun HistoryDoseCard(task: Task, completion: Completion?, accent: Color, now: LocalDateTime, onPendingClick: () -> Unit) {
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.dateTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = accent, fontWeight = FontWeight.Bold)
                Text(task.record.title, color = DarkText, fontWeight = FontWeight.SemiBold)
                if (task.record.detail.isNotBlank()) Text(task.record.detail, color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
            when (completion?.status) {
                Status.TAKEN -> Text("✓ Tomada", color = Color(0xFF16865B), fontWeight = FontWeight.Bold)
                Status.SKIPPED -> Text("Omitida", color = MutedText, fontWeight = FontWeight.SemiBold)
                null -> if (task.dateTime.isAfter(now)) Text("Programada", color = MutedText)
                    else if (task.record.archived) Text("Sin confirmar", color = MutedText)
                    else TextButton(onClick = onPendingClick) { Text("Pendiente", color = Color(0xFFB86A00)) }
            }
        }
    }
}
