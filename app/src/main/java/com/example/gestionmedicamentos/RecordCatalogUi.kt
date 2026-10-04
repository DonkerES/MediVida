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
internal fun RecordCard(r: HealthRecord, edit: (HealthRecord) -> Unit, accentOverride: Color? = null) {
    val accent = accentOverride ?: accentFor(r.kind)
    Panel {
        if (r.kind == Kind.APPOINTMENT) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(r.title.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), color = accent, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.SemiBold)
                    val date = runCatching { LocalDate.parse(r.start) }.getOrNull()
                    if (date != null) Text(date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es"))), style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
                val days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(r.start)) }.getOrDefault(Long.MAX_VALUE)
                if (days in 0..7) Text("Próxima", style = MaterialTheme.typography.labelSmall, color = AppointmentAccent, fontWeight = FontWeight.Bold)
            }
        } else Text(r.title, style = MaterialTheme.typography.titleLarge, color = accent)
        if (r.detail.isNotBlank()) Text(r.detail)
        if (r.kind == Kind.MEDICINE && r.stockRemaining != null) {
            val total = (r.stockTotal ?: r.stockRemaining).coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { r.stockRemaining.toFloat() / total }, modifier = Modifier.weight(1f).height(5.dp), color = accent, trackColor = accent.copy(alpha = .14f))
                Text("${r.stockRemaining}/${r.stockTotal ?: r.stockRemaining}", color = if (r.stockRemaining <= r.stockAlertAt) Color(0xFF9A5600) else MutedText, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (r.kind == Kind.MEDICINE) {
            Text("HORARIOS DE DOSIS", color = MutedText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.times.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { index, time ->
                    Column(Modifier.background(accent.copy(alpha = .09f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Dosis ${index + 1}", color = MutedText, style = MaterialTheme.typography.labelSmall)
                        Text(displayTime(time), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateInfo("INICIO", displayDate(r.start), Modifier.weight(1f), accent)
                DateInfo("FINAL", if (r.end.isBlank()) "Sin fecha final" else displayDate(r.end), Modifier.weight(1f), accent)
            }
        }
        if (r.kind == Kind.MEAL) {
            Text("${r.times} · ${r.start}${if (r.end.isNotBlank()) " a ${r.end}" else " · sin fecha final"}", color = MutedText)
        }
        if (r.kind == Kind.APPOINTMENT) {
            if (r.place.isNotBlank()) Text(r.place)
            Text(appointmentReminderSummary(r), style = MaterialTheme.typography.bodySmall, color = accent)
        }
        if (r.kind == Kind.RESTRICTION) Text("${if (isRestricted(r.level)) "🔴" else "🟢"} ${restrictionLabel(r.level)}", fontWeight = FontWeight.Bold)
        TextButton(onClick = { edit(r) }, colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text("Editar") }
    }
}

@Composable
private fun DateInfo(label: String, value: String, modifier: Modifier, accent: Color) {
    Column(modifier.background(Color(0xFFF5F8FC), RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MutedText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = accent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun displayDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es")))
}.getOrDefault(value)

private fun displayTime(value: String): String = runCatching {
    LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es")))
}.getOrDefault(value)

private fun isRestricted(level: String) = level == "Restringido" || level == "Prohibido"
private fun restrictionLabel(level: String) = when (level) {
    "Prohibido" -> "Restringido"
    "Moderado" -> "Revisar clasificación"
    else -> level
}

@Composable
private fun MealPlanCard(record: HealthRecord, edit: (HealthRecord) -> Unit) {
    val icon = when {
        record.title.contains("desay", true) -> "🌅"
        record.title.contains("almuer", true) -> "☀️"
        record.title.contains("cen", true) -> "🌙"
        else -> "🍽️"
    }
    val instructions = record.detail.lines().flatMap { it.split(";") }.map { it.trim().removePrefix("•").trim() }.filter { it.isNotBlank() }
    Card(Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0EAF3), RoundedCornerShape(13.dp)), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Text(record.title, Modifier.weight(1f), color = DarkText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFFF1EAFE), shape = RoundedCornerShape(8.dp)) {
                    Text(record.times.split(',').firstOrNull()?.trim()?.let(::displayTime) ?: record.times, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = NutritionAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (instructions.isNotEmpty()) {
                Column(Modifier.padding(start = 35.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    instructions.take(4).forEach { instruction ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = NutritionAccent, style = MaterialTheme.typography.bodySmall)
                            Text(instruction, color = DarkText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (instructions.size > 4) Text("+ ${instructions.size - 4} indicaciones", color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val dateRange = "Desde ${displayDate(record.start)}" + if (record.end.isBlank()) " · sin fecha final" else " · hasta ${displayDate(record.end)}"
                Text(dateRange, Modifier.weight(1f), color = MutedText, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { edit(record) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Editar", color = NutritionAccent) }
            }
        }
    }
}

@Composable
private fun AppointmentCard(record: HealthRecord, edit: (HealthRecord) -> Unit) {
    var details by rememberSaveable(record.id) { mutableStateOf(false) }
    val accent = AppointmentAccent
    val date = runCatching { LocalDate.parse(record.start) }.getOrNull()
    val days = date?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), it) }
    Card(Modifier.fillMaxWidth().border(1.dp, Color(0xFFB8F2D2), RoundedCornerShape(14.dp)), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(Color(0xFFE4F5FF), CircleShape), contentAlignment = Alignment.Center) {
                    Text(record.title.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), color = Color(0xFF1685B5), fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(record.title, color = DarkText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (record.detail.isNotBlank()) Text(record.detail, color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = Color(0xFFEAFBF4), shape = RoundedCornerShape(20.dp)) { Text("Cita", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color(0xFF078C67), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (date != null) LabelValue(Icons.Default.CalendarMonth, date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, Modifier.weight(1f))
                LabelValue(Icons.Default.AccessTime, record.times.split(',').firstOrNull()?.let(::displayTime) ?: record.times, Modifier.weight(1f))
            }
            if (record.place.isNotBlank()) LabelValue(Icons.Default.Place, record.place, Modifier.fillMaxWidth())
            if (days != null && days >= 0) Text(if (days == 0L) "Hoy" else "En $days días", color = Color(0xFF078C67), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { edit(record) }, modifier = Modifier.weight(1f)) { Text("Editar aviso") }
                Button(onClick = { details = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07966F))) { Text("Ver detalles") }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(record.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (date != null) Text(date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM yyyy", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) })
            Text("Hora: ${record.times.split(',').firstOrNull()?.let(::displayTime) ?: record.times}")
            if (record.detail.isNotBlank()) Text(record.detail)
            if (record.place.isNotBlank()) Text("Lugar: ${record.place}")
            Text(appointmentReminderSummary(record))
        } }, confirmButton = { TextButton(onClick = { details = false; edit(record) }) { Text("Editar cita") } }, dismissButton = { TextButton(onClick = { details = false }) { Text("Cerrar") } })
}

@Composable
private fun LabelValue(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = AppointmentAccent, modifier = Modifier.size(16.dp))
        Text(value, color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FoodRuleCard(record: HealthRecord, edit: (HealthRecord) -> Unit) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val restricted = isRestricted(record.level)
    val color = if (restricted) Color(0xFFEF4444) else if (record.level == "Moderado") Color(0xFFB7791F) else Color(0xFF08A875)
    val foods = record.foodItems.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }
    Card(Modifier.fillMaxWidth().border(1.dp, Color(0xFFE1EAF2), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(color.copy(alpha = .1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (restricted) Icons.Default.Block else Icons.Default.CheckCircle, null, tint = color)
                }
                Column(Modifier.weight(1f)) {
                    Text(record.title, color = DarkText, fontWeight = FontWeight.Bold)
                    Text(if (foods.isNotEmpty()) "${foods.size} ${if (foods.size == 1) "alimento" else "alimentos"}" else if (record.detail.isNotBlank()) record.detail else restrictionLabel(record.level), color = MutedText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Contraer" else "Expandir", tint = MutedText)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("${if (restricted) "Restringidos" else "Permitidos"}", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (foods.isNotEmpty()) {
                    Text(foods.joinToString(" · "), color = DarkText, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Aún no se han agregado alimentos a esta categoría.", color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
                if (record.detail.isNotBlank()) {
                    Text("Recomendación", color = MutedText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(record.detail, color = DarkText, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { edit(record) }) { Text("Editar categoría", color = color) }
            }
        }
    }
}

@Composable
internal fun Appointments(data: Snapshot, edit: (HealthRecord) -> Unit, add: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val all = data.records.filter { it.kind == Kind.APPOINTMENT && !it.archived }.sortedBy { it.start + it.times }
    val visible = all.filter { if (selected != null) it.start == selected else YearMonth.from(LocalDate.parse(it.start)) == month }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hero("Citas médicas", "Agenda y recordatorios", AppointmentGradient) }
        item { Box(Modifier.padding(horizontal = 16.dp)) { Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString(); selected = null }) { Icon(Icons.Default.ChevronLeft, "Mes anterior") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es"))), fontWeight = FontWeight.Bold)
                IconButton(onClick = { monthText = month.plusMonths(1).toString(); selected = null }) { Icon(Icons.Default.ChevronRight, "Mes siguiente") }
            }
            Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), color = MutedText) } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            (0 until (offset + month.lengthOfMonth() + 6) / 7).forEach { row -> Row {
                (0..6).forEach { col ->
                    val day = row * 7 + col - offset + 1
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = day in 1..month.lengthOfMonth()) { if (day in 1..month.lengthOfMonth()) selected = month.atDay(day).toString() }, contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day).toString()
                            val hasAppointment = all.any { it.start == date }
                            val selectedDay = selected == date
                            Box(Modifier.padding(2.dp).size(38.dp)
                                .background(when { selectedDay -> AppointmentAccent; hasAppointment -> AppointmentAccent.copy(alpha = .14f); else -> Color.Transparent }, CircleShape), contentAlignment = Alignment.Center) {
                                Text(day.toString(), color = if (selectedDay) Color.White else if (hasAppointment) AppointmentAccent else DarkText,
                                    fontWeight = if (selectedDay || hasAppointment) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected == null) "Los días verdes tienen citas. Selecciona un día para verlas." else "Mostrando solo las citas de $selected", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MutedText)
                if (selected != null) TextButton(onClick = { selected = null }) { Text("Ver mes") }
            }
        } } }
        if (visible.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyAction("No hay citas en este período.", "Agendar cita", add) } }
        items(visible, key = { it.id }) { r -> Box(Modifier.padding(horizontal = 16.dp)) { AppointmentCard(r, edit) } }
    }
}

@Composable
internal fun Nutrition(data: Snapshot, edit: (HealthRecord) -> Unit, add: () -> Unit) {
    var foodFilter by rememberSaveable { mutableStateOf("TODOS") }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hero("Nutrición", "Tus pautas de alimentación", NutritionGradient) }
        item { Text("Plan de comidas", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge) }
        val meals = data.records.filter { it.kind == Kind.MEAL && !it.archived }.sortedBy { it.times }
        if (meals.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyAction("Aún no hay comidas en tu plan.", "Agregar comida", add) } }
        items(meals, key = { it.id }) { r -> Box(Modifier.padding(horizontal = 16.dp)) { MealPlanCard(r, edit) } }
        item { Text("ALIMENTOS", Modifier.padding(horizontal = 16.dp), color = MutedText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TODOS" to "Todos", "PERMITIDOS" to "Permitidos", "RESTRINGIDOS" to "Restringidos").forEach { (id, label) ->
                    val tint = when (id) { "PERMITIDOS" -> Color(0xFF08A875); "RESTRINGIDOS" -> Color(0xFFEF4444); else -> NutritionAccent }
                    FilterChip(selected = foodFilter == id, onClick = { foodFilter = id }, label = { Text(label) },
                        leadingIcon = if (foodFilter == id) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tint.copy(alpha = .14f), selectedLabelColor = tint, selectedLeadingIconColor = tint))
                }
            }
        }
        val allFoods = data.records.filter { it.kind == Kind.RESTRICTION && !it.archived }
        val foods = allFoods.filter { when (foodFilter) { "PERMITIDOS" -> !isRestricted(it.level) && it.level != "Moderado"; "RESTRINGIDOS" -> isRestricted(it.level); else -> true } }
            .sortedBy { if (isRestricted(it.level)) 1 else if (it.level == "Moderado") 2 else 0 }
        if (foods.isEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { EmptyAction("Aún no hay categorías de alimentos.", "Agregar categoría", add) } }
        items(foods, key = { it.id }) { r -> Box(Modifier.padding(horizontal = 16.dp)) { FoodRuleCard(r, edit) } }
    }
}
