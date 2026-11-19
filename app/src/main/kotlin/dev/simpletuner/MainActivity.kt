package dev.simpletuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.simpletuner.ui.TunerScreen
import dev.simpletuner.ui.theme.TunerTheme
import dev.simpletuner.viewmodel.TunerViewModel

class MainActivity : ComponentActivity() {
    private val model: TunerViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) model.start()
        } else {
            model.permissionRequired(!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            TunerTheme {
                TunerScreen(state, model::selectMode, model::selectPrecision, ::requestMicrophone,
                    ::openSettings, model::start)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            model.start()
        } else {
            val asked = getPreferences(MODE_PRIVATE).getBoolean("permission_requested", false)
            model.permissionRequired(asked && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
            if (!asked) requestMicrophone()
        }
    }

    override fun onPause() {
        model.stop()
        super.onPause()
    }

    private fun requestMicrophone() {
        getPreferences(MODE_PRIVATE).edit { putBoolean("permission_requested", true) }
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}
