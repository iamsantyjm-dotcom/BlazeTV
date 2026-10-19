package com.pilltv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pilltv.app.ui.AppRoot
import com.pilltv.app.ui.PillTheme

/** Actividad de Android TV: sin barras de sistema ni modo ventana flotante (PiP). */
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PillTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.pause()
    }
}
