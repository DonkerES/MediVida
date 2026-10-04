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
internal fun Hero(title: String, subtitle: String, colors: List<Color> = HomeGradient, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(colors)).padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(subtitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("es")) else it.toString() }, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        content()
    }
}
@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable internal fun Empty(message: String) { Panel { Text(message, color = MutedText) } }
@Composable internal fun EmptyAction(message: String, label: String, onClick: () -> Unit) {
    Panel {
        Text(message, color = MutedText)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(label) }
    }
}
internal fun kindLabel(kind: Kind) = when (kind) { Kind.MEDICINE -> "Medicamento"; Kind.APPOINTMENT -> "Cita médica"; Kind.MEAL -> "Comida"; Kind.RESTRICTION -> "Restricción alimentaria" }
