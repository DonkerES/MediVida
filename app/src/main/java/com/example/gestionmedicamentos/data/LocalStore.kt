package com.example.gestionmedicamentos.data

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import org.json.JSONObject

/** Firebase is the source of truth; persistent cache survives process death and queues offline writes. */
class LocalStore private constructor(context: Context) : HealthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val sync = MutableStateFlow("Conectando…")
    private val local = LocalDatabase.get(context).dao()
    private val listenerExecutor = Executors.newSingleThreadExecutor()
    private val listeners = mutableListOf<ListenerRegistration>()
    private var listeningUser: String? = null
    private val metadata = mutableMapOf<String, SnapshotMetadata>()
    init { auth.addAuthStateListener { listen(); changes.tryEmit(Unit) } }
    override fun user(): String? = auth.currentUser?.uid
    private fun owner() = user() ?: error("Inicia sesión para acceder a tus datos.")
    private fun root() = db.collection("users").document(owner())
    private fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)
    private fun write(task: com.google.android.gms.tasks.Task<Void>) {
        task.addOnFailureListener { errors.tryEmit("El servidor rechazó un cambio. Revisa tu sesión y las reglas de Firebase."); changes.tryEmit(Unit) }
    }
    @Synchronized fun listen() {
        val uid = user()
        if (uid == listeningUser) return
        listeners.forEach { it.remove() }; listeners.clear(); metadata.clear(); listeningUser = uid
        if (uid == null) { sync.value = "Sin sesión"; return }
        fun changed(name: String, meta: SnapshotMetadata?, error: FirebaseFirestoreException?) {
            if (user() != uid) return
            if (error != null) errors.tryEmit("No se pudo sincronizar. Comprueba que Firestore esté creado y sus reglas publicadas.")
            if (meta != null) metadata[name] = meta
            sync.value = when {
                error != null -> "Error de sincronización"
                metadata.values.any { it.hasPendingWrites() } -> "Cambios pendientes de sincronizar"
                metadata.size < 3 || metadata.values.any { it.isFromCache } -> "Datos locales · esperando conexión"
                else -> "Sincronizado"
            }
            changes.tryEmit(Unit)
        }
        val ref = db.collection("users").document(uid)
        listeners += ref.addSnapshotListener(listenerExecutor, MetadataChanges.INCLUDE) { s, e ->
            if (s != null && s.exists() && user() == uid) local.putProfile(ProfileCacheRow(uid, JSONObject(s.data ?: emptyMap<String, Any>()).toString()))
            changed("profile", s?.metadata, e)
        }
        listeners += ref.collection("records").addSnapshotListener(listenerExecutor, MetadataChanges.INCLUDE) { s, e ->
            if (s != null && user() == uid) local.replaceRecords(uid, s.documents.map { RecordCacheRow(uid, it.id, JSONObject(it.data ?: emptyMap<String, Any>()).toString()) })
            changed("records", s?.metadata, e)
        }
        listeners += ref.collection("completions").addSnapshotListener(listenerExecutor, MetadataChanges.INCLUDE) { s, e ->
            if (s != null && user() == uid) local.replaceCompletions(uid, s.documents.map { CompletionCacheRow(uid, it.id, JSONObject(it.data ?: emptyMap<String, Any>()).toString()) })
            changed("completions", s?.metadata, e)
        }
    }
    override fun logout() {
        val uploaded = runCatching { await(db.waitForPendingWrites()) }.isSuccess
        check(uploaded) { "No se pudo confirmar la sincronización. Mantén esta sesión abierta, conéctate a internet y vuelve a intentarlo antes de cambiar de cuenta." }
        auth.signOut(); listen()
    }
    override fun register(name: String, email: String, password: String) {
        require(name.trim().length in 2..100) { "Escribe tu nombre (2 a 100 caracteres)." }
        require(password.length in 12..128) { "Usa una contraseña de 12 a 128 caracteres." }
        val result = await(auth.createUserWithEmailAndPassword(email.trim(), password))
        result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
        saveProfile(Profile(name = name.trim()))
        result.user?.sendEmailVerification()
        listen()
    }
    override fun login(email: String, password: String) { await(auth.signInWithEmailAndPassword(email.trim(), password)); listen() }
    override fun resetPassword(email: String) { require(email.isNotBlank()) { "Escribe tu correo." }; await(auth.sendPasswordResetEmail(email.trim())) }
    override fun changePassword(current: String, replacement: String) {
        require(replacement.length in 12..128) { "Usa una contraseña de 12 a 128 caracteres." }
        val user = auth.currentUser ?: error("Inicia sesión.")
        await(user.reauthenticate(EmailAuthProvider.getCredential(user.email ?: error("Cuenta sin correo."), current)))
        await(user.updatePassword(replacement))
    }
    override fun snapshot(): Snapshot {
        val uid = owner()
        val records = local.records(uid).map { it.decode() }
        val completions = local.completions(uid).map { it.decode() }
        val profile = local.profile(uid)?.decode() ?: Profile(name = auth.currentUser?.displayName ?: "Mi perfil")
        check(uid == user()) { "La sesión cambió; vuelve a intentarlo." }
        return Snapshot(records, completions, profile)
    }
    override fun save(record: HealthRecord) {
        Schedule.validate(record)
        val canonical = record.copy(times = Schedule.times(record.times).joinToString(",") { it.toString() })
        write(root().collection("records").document(record.id).set(encodeRecord(canonical)))
    }
    override fun delete(recordId: String) { write(root().collection("records").document(recordId).update("archived", true)) }
    override fun complete(recordId: String, occurrence: String, status: Status) {
        val data = snapshot()
        val r = data.records.firstOrNull { it.id == recordId && !it.archived } ?: error("Registro no disponible.")
        val time = LocalDateTime.parse(occurrence)
        require(Schedule.onDate(listOf(r), time.toLocalDate()).any { it.key == occurrence }) { "Horario no válido." }
        require(!time.isAfter(LocalDateTime.now())) { "La hora de esta tarea todavía no llegó." }
        if (data.completions.any { it.recordId == recordId && it.occurrence == occurrence }) return
        val eventId = recordId + "_" + occurrence.replace(":", "-")
        val batch = db.batch()
        batch.set(root().collection("completions").document(eventId), mapOf("recordId" to recordId, "occurrence" to occurrence, "status" to status.name, "at" to System.currentTimeMillis()))
        if (status == Status.TAKEN && r.kind == Kind.MEDICINE && r.stockRemaining != null) {
            batch.set(root().collection("records").document(recordId), encodeRecord(r.copy(stockRemaining = Schedule.stockAfterDose(r))))
        }
        write(batch.commit())
    }
    override fun saveProfile(profile: Profile) {
        require(profile.name.trim().length in 2..100) { "Escribe tu nombre." }
        require(profile.age.isBlank() || (profile.age.toIntOrNull() ?: -1) in 0..130) { "Edad no válida." }
        require(profile.weight.isBlank() || (profile.weight.toDoubleOrNull() ?: -1.0) in 0.1..500.0) { "Peso no válido (usa punto decimal)." }
        require(profile.blood in listOf("", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")) { "Tipo de sangre no válido." }
        require(profile.allergies.length <= 1000 && profile.contact.length <= 100 && profile.phone.length <= 30)
        require(profile.phone.isBlank() || Regex("[+0-9 ()-]{3,30}").matches(profile.phone)) { "Teléfono no válido." }
        write(root().set(mapOf("name" to profile.name, "age" to profile.age, "weight" to profile.weight, "blood" to profile.blood, "allergies" to profile.allergies, "contact" to profile.contact, "phone" to profile.phone, "notifications" to profile.notifications)))
    }
    companion object {
        @Volatile private var instance: LocalStore? = null
        fun configured(context: Context) = FirebaseApp.getApps(context).isNotEmpty()
        fun get(context: Context): LocalStore = instance ?: synchronized(this) { instance ?: LocalStore(context.applicationContext).also { instance = it } }
    }
}
private fun encodeRecord(r: HealthRecord) = mapOf("kind" to r.kind.name, "title" to r.title, "detail" to r.detail, "times" to r.times, "start" to r.start, "end" to r.end, "place" to r.place, "level" to r.level, "reminderMinutes" to r.reminderMinutes, "dayBefore" to r.dayBefore, "archived" to r.archived, "stockRemaining" to r.stockRemaining, "unitsPerDose" to r.unitsPerDose, "stockAlertAt" to r.stockAlertAt, "stockTotal" to r.stockTotal, "foodItems" to r.foodItems)
private fun decodeRecord(d: DocumentSnapshot): HealthRecord {
    val remaining = d.getLong("stockRemaining")?.toInt()
    return HealthRecord(d.id, Kind.valueOf(d.getString("kind")!!), d.getString("title")!!, d.getString("detail").orEmpty(), d.getString("times")!!, d.getString("start")!!, d.getString("end").orEmpty(), d.getString("place").orEmpty(), d.getString("level")!!, d.getLong("reminderMinutes")!!.toInt(), d.getBoolean("dayBefore")!!, d.getBoolean("archived") ?: false, remaining, d.getLong("unitsPerDose")?.toInt() ?: 1, d.getLong("stockAlertAt")?.toInt() ?: 5, d.getLong("stockTotal")?.toInt() ?: remaining, d.getString("foodItems").orEmpty())
}
