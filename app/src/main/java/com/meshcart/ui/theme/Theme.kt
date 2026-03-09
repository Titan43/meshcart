package com.meshcart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import com.meshcart.R

val Display = FontFamily(
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
)

val Body = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_bold, FontWeight.Bold),
)

// Warm organic palette
val Background    = Color(0xFFFAF7F2)   // warm cream
val Surface       = Color(0xFFFFFFFF)   // pure white cards
val SurfaceWarm   = Color(0xFFF5EFE6)   // warm tinted surface
val SurfaceTint   = Color(0xFFEDE4D8)   // pressed / hover state
val Border        = Color(0xFFE8DDD0)   // warm divider
val TextPrimary   = Color(0xFF2C2116)   // deep warm brown
val TextSecondary = Color(0xFF8C7B6B)   // mid warm brown
val TextMuted     = Color(0xFFBDAFA3)   // muted warm
val Accent        = Color(0xFFD95F2B)   // terracotta
val AccentLight   = Color(0xFFFAEDE5)   // accent tint background
val AccentDark    = Color(0xFFB04A1E)   // pressed accent
val Success       = Color(0xFF4A9E6E)   // sage green for checked items
val SuccessLight  = Color(0xFFE8F5EE)   // success tint
val Danger        = Color(0xFFCC3B3B)   // warm red

private val MeshColorScheme = lightColorScheme(
    primary           = Accent,
    onPrimary         = Color.White,
    primaryContainer  = AccentLight,
    onPrimaryContainer= AccentDark,
    secondary         = Success,
    onSecondary       = Color.White,
    background        = Background,
    onBackground      = TextPrimary,
    surface           = Surface,
    onSurface         = TextPrimary,
    surfaceVariant    = SurfaceWarm,
    onSurfaceVariant  = TextSecondary,
    outline           = Border,
    error             = Danger,
)

val MeshTypography = Typography(
    displayLarge  = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge    = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold,      fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium   = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge     = TextStyle(fontFamily = Body,    fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = Body,    fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge    = TextStyle(fontFamily = Body,    fontWeight = FontWeight.Medium,    fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelSmall    = TextStyle(fontFamily = Body,    fontWeight = FontWeight.Medium,    fontSize = 11.sp, letterSpacing = 0.5.sp),
)

@Composable
fun MeshCartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeshColorScheme,
        typography  = MeshTypography,
        content     = content
    )
}