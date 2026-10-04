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

@Composable
internal fun AuthScreen(state: AppState, vm: HealthViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    AuthBackground {
        HealthLogo(); Spacer(Modifier.height(20.dp))
        Text("MediVida", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Tu asistente personal de salud", color = Color.White); Spacer(Modifier.height(24.dp))
        AuthCard {
            Text(if (register) "Crear cuenta" else "Bienvenido", style = MaterialTheme.typography.headlineSmall, color = DarkText)
            Spacer(Modifier.height(12.dp))
            if (register) AuthTextField(name, { name = it }, "Nombre completo", Icons.Default.Person)
            AuthTextField(email, { email = it }, "Correo electrónico", Icons.Default.Email)
            AuthTextField(password, { password = it }, "Contraseña", Icons.Default.Lock, true)
            if (register) { AuthTextField(confirmation, { confirmation = it }, "Confirmar contraseña", Icons.Default.Lock, true); Text("Usa al menos 12 caracteres.", style = MaterialTheme.typography.bodySmall) }
            if (mismatch) Text("Las contraseñas no coinciden.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(18.dp))
            Button(enabled = !state.loading, onClick = { mismatch = register && password != confirmation; if (!mismatch) { if (register) vm.register(name, email, password) else vm.login(email, password) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text(if (register) "Crear cuenta" else "Entrar")
            }
            TextButton(enabled = !state.loading, onClick = { register = !register; password = ""; confirmation = ""; mismatch = false }) { Text(if (register) "Ya tengo una cuenta" else "Crear una cuenta") }
            if (!register) TextButton(enabled = !state.loading, onClick = { vm.reset(email) }) { Text("¿Olvidaste tu contraseña?") }
            Text("Tus datos se guardan asociados a tu cuenta. Necesitas conexión para iniciar sesión; los registros guardados estarán disponibles sin conexión.", style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
    }
}
