package com.example.gestionmedicamentos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero("MediVida", "Acerca de la aplicación")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Volver al perfil") }
            Panel {
                Text("Tu rutina de salud en un lugar", style = MaterialTheme.typography.titleLarge)
                Text("MediVida organiza medicamentos, citas médicas y comidas en una agenda diaria. Permite registrar las tareas realizadas y consultar un historial de tomas y omisiones.")
            }
            Panel {
                Text("Recordatorios claros", style = MaterialTheme.typography.titleLarge)
                Text("Las alertas indican la tarea y su horario. Puedes confirmar una toma o posponer un aviso diez minutos. Los horarios y las dosis deben seguir las indicaciones de tu profesional de salud.")
            }
            Panel {
                Text("Datos y seguridad", style = MaterialTheme.typography.titleLarge)
                Text("En modo de prueba, tus datos se guardan solo en este dispositivo. Con una cuenta, tus registros se sincronizan y puedes consultarlos sin conexión.")
            }
            Panel {
                Text("Equipo de desarrollo", style = MaterialTheme.typography.titleLarge)
                Text("MediVida fue desarrollada como proyecto académico por:", color = MutedText)
                Text("Julian Andres Narváez Cerón\nSebastian Alejandro Muñoz", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            Text("MediVida es una herramienta de organización y no sustituye la valoración médica.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
