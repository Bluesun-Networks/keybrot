package com.zooptype.ztype.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.zooptype.ztype.R
import com.zooptype.ztype.theme.Theme

/**
 * Settings Activity for Keybrot.
 *
 * Launched from the app icon. Provides configuration for:
 * - Theme selection
 * - Node density
 * - Haptic intensity
 * - Adaptive learning toggle
 * - Debug overlay toggle
 * - IME setup instructions
 *
 * This Activity uses Keybrot as its input method — dogfooding!
 */
class ZTypeSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Build the UI programmatically (keeping it simple for v1)
        val scrollView = ScrollView(this).apply {
            setPadding(32, 32, 32, 32)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // --- Header ---
        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 32f
            setTextColor(0xFF00FFFF.toInt())
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "The Mandelbrot Keyboard"
            textSize = 16f
            setTextColor(0xAAFFFFFF.toInt())
            setPadding(0, 0, 0, 32)
        })

        // --- IME Setup Section ---
        layout.addView(sectionHeader("Setup"))

        layout.addView(Button(this).apply {
            text = "Enable Keybrot in System Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })

        layout.addView(Button(this).apply {
            text = "Switch to Keybrot"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        })

        layout.addView(spacer())

        // --- Theme Section ---
        layout.addView(sectionHeader(getString(R.string.settings_category_appearance)))

        val themes = Theme.values()
        val themeNames = arrayOf(
            getString(R.string.settings_theme_cyber),
            getString(R.string.settings_theme_glass),
            getString(R.string.settings_theme_solar)
        )

        val currentTheme = prefs.getString("theme", Theme.CYBER_LUMINESCENT.name)
        for ((i, theme) in themes.withIndex()) {
            layout.addView(Button(this).apply {
                text = themeNames[i]
                alpha = if (theme.name == currentTheme) 1f else 0.5f
                setOnClickListener {
                    prefs.edit().putString("theme", theme.name).apply()
                    recreate() // Refresh to show selection
                }
            })
        }

        layout.addView(spacer())

        // --- Node Density Section ---
        layout.addView(sectionHeader(getString(R.string.settings_node_density)))

        val densityOptions = arrayOf(
            "minimal" to getString(R.string.settings_node_density_minimal),
            "standard" to getString(R.string.settings_node_density_standard),
            "full" to getString(R.string.settings_node_density_full)
        )

        val currentDensity = prefs.getString("node_density", "standard")
        for ((key, label) in densityOptions) {
            layout.addView(Button(this).apply {
                text = label
                alpha = if (key == currentDensity) 1f else 0.5f
                setOnClickListener {
                    prefs.edit().putString("node_density", key).apply()
                    recreate()
                }
            })
        }

        layout.addView(spacer())

        // --- Haptics Section ---
        layout.addView(sectionHeader(getString(R.string.settings_category_input)))

        layout.addView(switchSetting(
            getString(R.string.settings_haptics),
            "haptics_enabled",
            true
        ))

        layout.addView(TextView(this).apply {
            text = getString(R.string.settings_haptic_intensity)
            setPadding(0, 16, 0, 8)
        })

        layout.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("haptic_intensity", 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) prefs.edit().putInt("haptic_intensity", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })

        layout.addView(spacer())

        // --- Learning Section ---
        layout.addView(switchSetting(
            getString(R.string.settings_adaptive_learning),
            "adaptive_learning",
            true
        ))

        layout.addView(spacer())

        // --- Developer Section ---
        layout.addView(sectionHeader(getString(R.string.settings_category_developer)))

        layout.addView(switchSetting(
            getString(R.string.settings_debug_overlay),
            "debug_overlay",
            true
        ))

        // --- Test Input Field ---
        layout.addView(spacer())
        layout.addView(sectionHeader("Test Input"))
        layout.addView(TextView(this).apply {
            text = "Tap below to test Keybrot:"
            setPadding(0, 0, 0, 8)
        })

        layout.addView(android.widget.EditText(this).apply {
            hint = "Type here with Keybrot..."
            setPadding(16, 16, 16, 16)
            minHeight = 120
        })

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun sectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(0xFF00FFFF.toInt())
            setPadding(0, 24, 0, 8)
        }
    }

    private fun spacer(): android.view.View {
        return android.view.View(this).apply {
            minimumHeight = 24
        }
    }

    private fun switchSetting(label: String, key: String, default: Boolean): Switch {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return Switch(this).apply {
            text = label
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).apply()
            }
        }
    }
}
