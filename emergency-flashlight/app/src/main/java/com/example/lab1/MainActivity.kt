package com.example.lab1

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.lab1.flashlight.FlashlightScreen
import com.example.lab1.flashlight.FlashlightViewModel
import com.example.lab1.flashlight.FlashlightAction
import com.example.lab1.ui.theme.Lab1Theme

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[FlashlightViewModel::class.java] }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.onAction(FlashlightAction.PermissionResult)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lab1Theme {
                FlashlightScreen(model, onRequestCamera = { permission.launch(Manifest.permission.CAMERA) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.onAction(FlashlightAction.Start)
    }

    override fun onPause() {
        model.onAction(FlashlightAction.Stop)
        super.onPause()
    }
}
