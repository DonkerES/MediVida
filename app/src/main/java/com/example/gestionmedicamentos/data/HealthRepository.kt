package com.example.gestionmedicamentos.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface HealthRepository {
    val changes: SharedFlow<Unit>
    val errors: SharedFlow<String>
    val sync: StateFlow<String>
    fun user(): String?
    fun snapshot(): Snapshot
    fun logout()
    fun register(name: String, email: String, password: String)
    fun login(email: String, password: String)
    fun resetPassword(email: String)
    fun changePassword(current: String, replacement: String)
    fun save(record: HealthRecord)
    fun delete(recordId: String)
    fun complete(recordId: String, occurrence: String, status: Status)
    fun saveProfile(profile: Profile)
}
