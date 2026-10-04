package com.example.gestionmedicamentos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestionmedicamentos.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.google.firebase.auth.FirebaseAuthException

// Datos compartidos que Compose muestra en todas las pantallas.
data class AppState(
    val loading: Boolean = true,
    val user: String? = null,
    val snapshot: Snapshot = Snapshot(),
    val error: String? = null,
    val configured: Boolean = true,
    val sync: String = "",
    val message: String? = null
)

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()

    private val firebaseConfigured = LocalStore.configured(application)
    private val repository: HealthRepository by lazy {
        if (firebaseConfigured) LocalStore.get(application) else DemoStore.get(application)
    }
    private val reminders by lazy { Reminders.get(application) }
    private var hasStarted = false

    init {
        refresh()
        viewModelScope.launch {
            repository.changes.collect {
                delay(150)
                while (_state.value.loading) delay(100)
                refresh()
            }
        }
        viewModelScope.launch {
            repository.errors.collect { error ->
                _state.value = _state.value.copy(error = error)
            }
        }
        viewModelScope.launch {
            repository.sync.collect { syncStatus ->
                _state.value = _state.value.copy(sync = syncStatus)
            }
        }
    }

    private fun performOperation(
        action: () -> Unit,
        clearNotices: Boolean = true,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        // Evita iniciar dos operaciones a la vez desde la interfaz.
        if (_state.value.loading && hasStarted) return
        hasStarted = true
        _state.value = _state.value.copy(loading = true, error = if (clearNotices) null else _state.value.error)

        viewModelScope.launch {
            try {
                // Room y Firebase pueden tardar; se ejecutan fuera del hilo de la pantalla.
                val next = withContext(Dispatchers.IO) {
                    action()
                    val userId = repository.user()
                    val snapshot = if (userId != null) repository.snapshot() else Snapshot()
                    reminders.rebuild(repository)
                    // Compose recibe el nuevo estado y vuelve a dibujar lo necesario.
                    AppState(
                        user = userId,
                        snapshot = snapshot,
                        loading = false,
                        configured = firebaseConfigured,
                        sync = repository.sync.value
                    )
                }
                _state.value = next.copy(error = _state.value.error, message = _state.value.message)
                onSuccess()
            } catch (e: Exception) {
                val cause = e.cause ?: e
                val message = when {
                    cause is FirebaseAuthException -> "No se pudo autenticar. Revisa el correo y la contraseña, o utiliza recuperar contraseña."
                    cause is IllegalArgumentException || cause is IllegalStateException -> cause.message
                    else -> "No se pudo completar la operación. Revisa tu conexión y vuelve a intentarlo."
                }
                _state.value = _state.value.copy(loading = false, error = message)
                onFailure()
            }
        }
    }

    fun refresh() = performOperation(action = {}, clearNotices = false)
    fun enterDemo() = performOperation(action = { (repository as DemoStore).activate() })
    fun login(email: String, password: String) = performOperation(action = { repository.login(email, password) })
    fun register(name: String, email: String, password: String) = performOperation(action = { repository.register(name, email, password) })
    fun logout() = performOperation(action = { repository.logout(); reminders.cancel() })
    fun reset(email: String) = performOperation(
        action = { repository.resetPassword(email) },
        onSuccess = {
            _state.value = _state.value.copy(message = "Si existe una cuenta para ese correo, recibirás instrucciones para restablecer la contraseña.")
        }
    )
    fun save(record: HealthRecord, done: () -> Unit) = performOperation(action = { repository.save(record) }, onSuccess = done)
    fun delete(id: String, done: () -> Unit) = performOperation(action = { repository.delete(id) }, onSuccess = done)
    fun profile(profile: Profile, done: () -> Unit) = performOperation(action = { repository.saveProfile(profile) }, onSuccess = done)

    fun complete(task: Task, status: Status, done: () -> Unit = {}, failed: () -> Unit = {}) {
        viewModelScope.launch {
            while (_state.value.loading) delay(100)
            performOperation(
                action = { repository.complete(task.record.id, task.key, status) },
                onSuccess = done,
                onFailure = failed
            )
        }
    }
    fun snooze(task: Task) = performOperation(action = { reminders.snooze(repository, task.record.id, task.key) })
    fun password(old: String, new: String, done: () -> Unit) = performOperation(action = { repository.changePassword(old, new) }, onSuccess = done)
    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
