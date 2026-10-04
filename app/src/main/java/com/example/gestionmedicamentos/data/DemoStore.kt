package com.example.gestionmedicamentos.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.time.LocalDateTime

/** Isolated local practice data. Never sent to Firebase or used as an authenticated account. */
class DemoStore private constructor(context: Context) : HealthRepository {
    private val preferences = context.getSharedPreferences("practice_session", Context.MODE_PRIVATE)
    private val dao = LocalDatabase.get(context).dao()
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val sync = MutableStateFlow("Modo de prueba · guardado solo en este teléfono")
    private val owner = "demo-local"

    override fun user(): String? = if (preferences.getBoolean("active", false)) owner else null

    fun activate() {
        check(preferences.edit().putBoolean("active", true).commit()) { "No se pudo guardar el modo de prueba." }
        if (dao.profile(owner) == null) saveProfile(Profile(name = "Modo de prueba"))
        changes.tryEmit(Unit)
    }

    override fun logout() {
        check(preferences.edit().putBoolean("active", false).commit())
        changes.tryEmit(Unit)
    }

    private fun requireActive() { check(user() == owner) { "Abre el modo de prueba para consultar estos datos." } }

    override fun snapshot(): Snapshot {
        requireActive()
        return Snapshot(dao.records(owner).map { it.decode() }, dao.completions(owner).map { it.decode() },
            dao.profile(owner)?.decode() ?: Profile(name = "Modo de prueba"))
    }

    override fun save(record: HealthRecord) {
        requireActive()
        Schedule.validate(record)
        val normalized = record.copy(times = Schedule.times(record.times).joinToString(",") { it.toString() })
        val j = JSONObject().put("kind", normalized.kind.name).put("title", normalized.title)
            .put("detail", normalized.detail).put("times", normalized.times).put("start", normalized.start)
            .put("end", normalized.end).put("place", normalized.place).put("level", normalized.level)
            .put("reminderMinutes", normalized.reminderMinutes).put("dayBefore", normalized.dayBefore)
            .put("archived", normalized.archived).put("stockRemaining", normalized.stockRemaining)
            .put("unitsPerDose", normalized.unitsPerDose).put("stockAlertAt", normalized.stockAlertAt)
            .put("stockTotal", normalized.stockTotal ?: normalized.stockRemaining).put("foodItems", normalized.foodItems)
        val previous = dao.records(owner).firstOrNull { it.id == record.id }?.decode()
        require(previous == null || (!previous.archived && previous.kind == record.kind)) { "Este registro está archivado." }
        dao.putRecords(listOf(RecordCacheRow(owner, record.id, j.toString())))
        changes.tryEmit(Unit)
    }

    override fun delete(recordId: String) {
        requireActive()
        val row = dao.records(owner).firstOrNull { it.id == recordId } ?: error("Registro no disponible.")
        val j = JSONObject(row.payload).put("archived", true)
        dao.putRecords(listOf(row.copy(payload = j.toString())))
        changes.tryEmit(Unit)
    }

    override fun complete(recordId: String, occurrence: String, status: Status) {
        requireActive()
        val record = snapshot().records.firstOrNull { it.id == recordId && !it.archived } ?: error("Registro no disponible.")
        val time = LocalDateTime.parse(occurrence)
        require(!time.isAfter(LocalDateTime.now())) { "La hora de esta tarea todavía no llegó." }
        require(Schedule.onDate(listOf(record), time.toLocalDate()).any { it.key == occurrence }) { "Horario no válido." }
        val id = recordId + "_" + occurrence.replace(":", "-")
        if (dao.completions(owner).any { it.id == id }) return
        val json = JSONObject().put("recordId", recordId).put("occurrence", occurrence)
            .put("status", status.name).put("at", System.currentTimeMillis())
        val recordRow = dao.records(owner).first { it.id == recordId }
        val updatedRecord = if (status == Status.TAKEN && record.kind == Kind.MEDICINE && record.stockRemaining != null) {
            recordRow.copy(payload = JSONObject(recordRow.payload)
                .put("stockRemaining", Schedule.stockAfterDose(record)).toString())
        } else null
        dao.putCompletion(owner, CompletionCacheRow(owner, id, json.toString()), updatedRecord)
        changes.tryEmit(Unit)
    }

    override fun saveProfile(profile: Profile) {
        requireActive()
        require(profile.name.trim().length in 2..100) { "Escribe tu nombre." }
        require(profile.age.isBlank() || (profile.age.toIntOrNull() ?: -1) in 0..130) { "Edad no válida." }
        require(profile.weight.isBlank() || (profile.weight.toDoubleOrNull() ?: -1.0) in 0.1..500.0) { "Peso no válido." }
        require(profile.allergies.length <= 1000 && profile.contact.length <= 100 && profile.phone.length <= 30)
        val json = JSONObject().put("name", profile.name.trim()).put("age", profile.age).put("weight", profile.weight)
            .put("blood", profile.blood).put("allergies", profile.allergies).put("contact", profile.contact)
            .put("phone", profile.phone).put("notifications", profile.notifications)
        dao.putProfile(ProfileCacheRow(owner, json.toString()))
        changes.tryEmit(Unit)
    }

    override fun register(name: String, email: String, password: String): Unit = error("El modo de prueba no crea cuentas.")
    override fun login(email: String, password: String): Unit = error("El modo de prueba no crea cuentas.")
    override fun resetPassword(email: String): Unit = error("El modo de prueba no crea cuentas.")
    override fun changePassword(current: String, replacement: String): Unit = error("El modo de prueba no crea cuentas.")

    companion object {
        @Volatile private var instance: DemoStore? = null
        fun get(context: Context): DemoStore = instance ?: synchronized(this) {
            instance ?: DemoStore(context.applicationContext).also { instance = it }
        }
    }
}
