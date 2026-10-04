package com.example.gestionmedicamentos.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable
fun GestionMedicamentosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF078AA5),
            onPrimary = Color.White,
            secondary = Color(0xFF008A65),
            tertiary = Color(0xFF7B4BC4),
            background = Color(0xFFF5F8FC),
            surface = Color.White,
            onSurface = Color(0xFF071B3A),
            surfaceVariant = Color(0xFFEAF1F5),
            onSurfaceVariant = Color(0xFF596B86)
        ),
        typography = Typography,
        content = content
    )
}
