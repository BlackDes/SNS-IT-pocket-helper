package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object ThemeManager {
    var isDark by mutableStateOf(false)
}

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Official-style SNS brand colors for IT application - Frosted Glass Theme in Green Tones
val SnsDarkBg: Color
    get() = if (ThemeManager.isDark) Color(0xFF0C130F) else Color(0xFFE8F0EC)

val SnsCardBg: Color
    get() = if (ThemeManager.isDark) Color(0xD9152219) else Color(0xD9FFFFFF)

val SnsRedAccent: Color // Main vibrant green brand accent
    get() = if (ThemeManager.isDark) Color(0xFF4ADE80) else Color(0xFF15803D)

val SnsSilverText: Color
    get() = if (ThemeManager.isDark) Color(0xFFECFDF5) else Color(0xFF111E16)

val SnsSecondaryText: Color
    get() = if (ThemeManager.isDark) Color(0xFF94A89C) else Color(0xFF475F50)

val SnsBorder: Color
    get() = if (ThemeManager.isDark) Color(0x334ADE80) else Color(0x1F111E16)

val SnsOrange: Color
    get() = if (ThemeManager.isDark) Color(0xFFF97316) else Color(0xFFEA580C)

val SnsGreen: Color
    get() = if (ThemeManager.isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
