package com.example.gestionmedicamentos

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gestionmedicamentos.ui.theme.GestionMedicamentosTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            GestionMedicamentosTheme {
                AlarmScreen(
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty(),
                    occurrence = intent.getStringExtra(EXTRA_OCCURRENCE).orEmpty(),
                    kind = intent.getStringExtra(EXTRA_KIND).orEmpty(),
                    onTake = { finishWith(ReminderReceiver.ACTION_TAKE) },
                    onSnooze = { finishWith(ReminderReceiver.ACTION_SNOOZE) }
                )
            }
        }
    }

    private fun finishWith(action: String) {
        val alarmUri = intent.data ?: run { finish(); return }
        sendBroadcast(Intent(this, ReminderReceiver::class.java).setAction(action).setData(alarmUri))
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_DETAIL = "alarm_detail"
        const val EXTRA_OCCURRENCE = "alarm_occurrence"
        const val EXTRA_KIND = "alarm_kind"
    }
}

@Composable
private fun AlarmScreen(title: String, detail: String, occurrence: String, kind: String, onTake: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    var unlocked by remember { mutableStateOf(!keyguard.isKeyguardLocked) }
    var confirmTake by remember { mutableStateOf(false) }
    val dateTime = runCatching { LocalDateTime.parse(occurrence) }.getOrNull()
    val isMedicine = kind == "MEDICINE"
    val heading = when (kind) { "APPOINTMENT" -> "HORA DE TU CITA"; "MEAL" -> "HORA DE COMER"; else -> "HORA DE TU DOSIS" }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F8FC)) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Medication, null, tint = Color.White, modifier = Modifier.background(HomeAccent, CircleShape).padding(18.dp).size(34.dp))
            Spacer(Modifier.height(22.dp))
            Text(heading, color = HomeAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (!unlocked) "Desbloquea para ver los detalles" else title.ifBlank { "Medicamento" }, color = DarkText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (unlocked && detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(detail, color = MutedText, style = MaterialTheme.typography.bodyLarge)
            }
            if (dateTime != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccessTime, null, tint = HomeAccent)
                    Text(dateTime.format(DateTimeFormatter.ofPattern("h:mm a · EEEE d MMMM", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, color = MutedText)
                }
            }
            Spacer(Modifier.height(30.dp))
            Button(onClick = {
                if (unlocked) confirmTake = true
                else if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() { unlocked = true; confirmTake = true }
                    })
                }
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF159564))) {
                Text(if (!unlocked) "Desbloquear para confirmar" else if (isMedicine) "Confirmar toma" else "Confirmar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Recordar en 10 minutos", color = HomeAccent)
            }
        }
    }
    if (confirmTake) AlertDialog(
        onDismissRequest = { confirmTake = false },
        title = { Text(if (isMedicine) "¿Confirmas que tomaste la dosis?" else "¿Confirmas que completaste esto?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title)
            if (detail.isNotBlank()) Text(detail, color = MutedText)
            if (dateTime != null) Text(dateTime.format(DateTimeFormatter.ofPattern("HH:mm · d MMMM", Locale.forLanguageTag("es"))), color = MutedText)
        } },
        confirmButton = { TextButton(onClick = { confirmTake = false; onTake() }) { Text("Sí, confirmar") } },
        dismissButton = { TextButton(onClick = { confirmTake = false }) { Text("Todavía no") } }
    )
}
