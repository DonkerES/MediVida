package com.example.gestionmedicamentos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gestionmedicamentos.ui.theme.GestionMedicamentosTheme


val PrimaryBlue = Color(0xFF0099B8)
val HomeAccent = Color(0xFF078AA5)
val MedicationAccent = Color(0xFF0B879D)
val MedicationCardAccents = listOf(Color(0xFF00A3C7), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFFF59E0B))
val AppointmentAccent = Color(0xFF008A65)
val NutritionAccent = Color(0xFF7B4BC4)
val HomeGradient = listOf(Color(0xFF087F9C), Color(0xFF12B8C6))
val MedicationGradient = listOf(Color(0xFF087F9C), Color(0xFF12AFC0))
val AppointmentGradient = listOf(Color(0xFF087B61), Color(0xFF20B87E))
val NutritionGradient = listOf(Color(0xFF6541A5), Color(0xFF9A6BE0))
val DarkText = Color(0xFF071B3A)
val BackgroundColor = Color(0xFFF5F8FC)
val MutedText = Color(0xFF596B86)

fun accentFor(kind: com.example.gestionmedicamentos.data.Kind): Color = when (kind) {
    com.example.gestionmedicamentos.data.Kind.MEDICINE -> MedicationAccent
    com.example.gestionmedicamentos.data.Kind.APPOINTMENT -> AppointmentAccent
    com.example.gestionmedicamentos.data.Kind.MEAL,
    com.example.gestionmedicamentos.data.Kind.RESTRICTION -> NutritionAccent
}

@Composable
fun AuthBackground(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E8EAA),
                        Color(0xFF0BA3B8),
                        Color(0xFFF5F8FC)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 42.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            content = content
        )
    }
}

@Composable
fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            content = content
        )
    }
}

@Composable
fun HealthLogo() {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true
    )
}


@Composable
fun SummaryBox(number: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(number, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}


@Composable
fun ProgressCircle(
    progress: Float,
    color: Color,
    trackColor: Color,
    value: String,
    label: String,
    labelColor: Color
) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(78.dp),
            color = color,
            strokeWidth = 7.dp,
            trackColor = trackColor
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontWeight = FontWeight.Bold)
            Text(label, color = labelColor, fontSize = 9.sp)
        }
    }
}
