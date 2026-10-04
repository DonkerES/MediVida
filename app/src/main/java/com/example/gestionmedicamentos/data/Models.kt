package com.example.gestionmedicamentos.data

import java.time.*
import java.util.UUID

enum class Kind { MEDICINE, APPOINTMENT, MEAL, RESTRICTION }
enum class Status { TAKEN, SKIPPED }

data class HealthRecord(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind,
    val title: String,
    val detail: String = "",
    val times: String = "08:00",
    val start: String = LocalDate.now().toString(),
    val end: String = "",
    val place: String = "",
    val level: String = "Permitido",
    val reminderMinutes: Int = 0,
    val dayBefore: Boolean = false,
    val archived: Boolean = false,
    val stockRemaining: Int? = null,
    val unitsPerDose: Int = 1,
    val stockAlertAt: Int = 5,
    val stockTotal: Int? = null,
    val foodItems: String = ""
)

data class Profile(
    val name: String = "", val age: String = "", val weight: String = "",
    val blood: String = "", val allergies: String = "",
    val contact: String = "", val phone: String = "", val notifications: Boolean = true
)
data class Completion(val recordId: String, val occurrence: String, val status: Status, val at: Long)
data class Task(val record: HealthRecord, val dateTime: LocalDateTime) {
    val key: String get() = dateTime.toString()
    val epoch: Long get() = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
data class Snapshot(val records: List<HealthRecord> = emptyList(), val completions: List<Completion> = emptyList(), val profile: Profile = Profile())

object Schedule {
    fun times(value: String): List<LocalTime> = value.split(",").map {
        require(Regex("\\d{2}:\\d{2}").matches(it.trim())) { "Usa horas HH:mm separadas por comas (08:00, 16:00)." }
        LocalTime.parse(it.trim())
    }.distinct().sorted()

    fun validate(r: HealthRecord) {
        require(r.title.isNotBlank() && r.title.length <= 100) { "Escribe un nombre de hasta 100 caracteres." }
        require(r.detail.length <= 1000 && r.place.length <= 200 && r.foodItems.length <= 2000) { "El texto es demasiado largo." }
        if (r.kind == Kind.RESTRICTION) {
            require(r.level in listOf("Restringido", "Permitido"))
            return
        }
        if (r.kind == Kind.MEDICINE) {
            require(r.stockRemaining == null || r.stockRemaining in 0..100000) { "La existencia debe estar entre 0 y 100000 unidades." }
            require(r.stockTotal == null || r.stockTotal in 0..100000) { "El total de stock no es válido." }
            require(r.stockRemaining == null || r.stockTotal == null || r.stockRemaining <= r.stockTotal) { "La existencia actual no puede superar el total recibido." }
            require(r.unitsPerDose in 1..1000) { "Las unidades por dosis deben estar entre 1 y 1000." }
            require(r.stockAlertAt in 0..100000) { "El umbral de stock no es válido." }
        }
        val start = LocalDate.parse(r.start)
        if (r.kind == Kind.APPOINTMENT) require(!start.isBefore(LocalDate.now())) { "La fecha de la cita debe ser hoy o posterior." }
        if (r.end.isNotBlank()) require(!LocalDate.parse(r.end).isBefore(start)) { "La fecha final debe ser igual o posterior al inicio." }
        val times = times(r.times)
        require(times.isNotEmpty() && times.size <= 24) { "Indica entre 1 y 24 horarios." }
        require(r.times.split(',').map { it.trim() }.distinct().size == r.times.split(',').size) { "Cada horario debe ser distinto." }
        if (r.kind == Kind.APPOINTMENT) require(times.size == 1) { "La cita necesita una sola hora." }
        if (r.kind == Kind.MEDICINE) require(r.detail.isNotBlank()) { "Indica la dosis y las instrucciones prescritas." }
        require(r.reminderMinutes in 0..10080) { "Selecciona un aviso previo válido para la cita." }
    }

    fun onDate(records: List<HealthRecord>, date: LocalDate, includeArchived: Boolean = false): List<Task> = records.flatMap { r ->
        if ((r.archived && !includeArchived) || r.kind == Kind.RESTRICTION || date < LocalDate.parse(r.start) ||
            (r.end.isNotBlank() && date > LocalDate.parse(r.end)) ||
            (r.kind == Kind.APPOINTMENT && date != LocalDate.parse(r.start))) emptyList()
        else times(r.times).map { Task(r, date.atTime(it)) }
    }.sortedBy { it.dateTime }

    fun agenda(records: List<HealthRecord>, now: LocalDateTime): List<Task> =
        onDate(records, now.toLocalDate()).filter { it.dateTime.isAfter(now) }

    fun pendingOnDate(records: List<HealthRecord>, date: LocalDate, completions: List<Completion>): List<Task> =
        onDate(records, date).filter { task ->
            completions.none { it.recordId == task.record.id && it.occurrence == task.key }
        }

    fun stockAfterDose(record: HealthRecord): Int? = record.stockRemaining?.let {
        (it - record.unitsPerDose).coerceAtLeast(0)
    }

    fun adherence(tasks: List<Task>, completions: List<Completion>, now: LocalDateTime): Int? {
        val due = tasks.filter { !it.dateTime.isAfter(now) }
        if (due.isEmpty()) return null
        val taken = due.count { task -> completions.any { it.recordId == task.record.id && it.occurrence == task.key && it.status == Status.TAKEN } }
        return taken * 100 / due.size
    }

    fun next(r: HealthRecord, after: LocalDateTime): Task? {
        if (r.kind == Kind.RESTRICTION) return null
        val first = maxOf(LocalDate.parse(r.start), after.toLocalDate())
        return (0..1).flatMap { onDate(listOf(r), first.plusDays(it.toLong())) }.firstOrNull { it.dateTime > after }
    }
}
