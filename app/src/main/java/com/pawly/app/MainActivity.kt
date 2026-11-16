package com.pawly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("pawly_appearance", MODE_PRIVATE)
        setContent {
            var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
            var accent by remember { mutableStateOf(preferences.getString("accent", "forest") ?: "forest") }
            var designStyle by remember { mutableStateOf(preferences.getString("design_style", "simple") ?: "simple") }
            PawlyTheme(darkMode = darkMode, accent = accent) {
                PawlyApp(
                    darkMode = darkMode,
                    accent = accent,
                    designStyle = designStyle,
                    onDesignStyleChange = { designStyle = it; preferences.edit().putString("design_style", it).apply() },
                    onDarkModeChange = { darkMode = it; preferences.edit().putBoolean("dark_mode", it).apply() },
                    onAccentChange = { accent = it; preferences.edit().putString("accent", it).apply() }
                )
            }
        }
    }
}
