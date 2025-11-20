@file:OptIn(ExperimentalMaterial3Api::class)

package com.getteli.lightandnight

import android.content.ContentResolver
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.getteli.lightandnight.ui.theme.AppTheme
import java.time.LocalTime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var targetSensor: Sensor? = null

    // Estado reativo para armazenar os valores do sensor
    private val sensorValues = mutableStateListOf<String>()

    // Estados reativos compartilhados com o TileService
    private var isAutoBrightnessEnabled by QuickSettingsTileService.isAutoBrightnessEnabled
    private var isAutoThemeEnabled by QuickSettingsTileService.isAutoThemeEnabled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicia o serviço
        startForegroundService()

        // Inicializa o SensorManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Obtém a lista de todos os sensores
        val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)

        // Procura pelo sensor específico
        targetSensor = sensorList.find { it.name == "back_als_str" || it.type == 33171095 }

        if (!Settings.System.canWrite(this)) {
            // Cria um Intent para abrir a tela de configurações de permissões
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        }

        setContent {
            // Estados reativos para os valores dos sensores
            var sensorValue1 by remember { mutableStateOf<String>("não encontrado") }
            var sensorValue2 by remember { mutableStateOf<String>("não encontrado") }

            // Snapshot da lista de valores do sensor
            val sensorValuesSnapshot by remember { derivedStateOf { sensorValues.toList() } }

            // Observa mudanças nos valores do sensor
            LaunchedEffect(sensorValuesSnapshot) {
                sensorValue1 = sensorValuesSnapshot.getOrNull(2) ?: "não encontrado"
                sensorValue2 = sensorValuesSnapshot.getOrNull(3) ?: "não encontrado"

                // Log para depuração
                // println("Sensor 1: $sensorValue1, Sensor 2: $sensorValue2")
            }

            // Define o tema com base no estado do Switch de tema automático
            val currentHour = LocalTime.now().hour
            val isDarkModeByTime = currentHour >= 17 || currentHour < 6 // Entre 17:00 e 5:59 é escuro
            val useDarkTheme = if (isAutoThemeEnabled) isDarkModeByTime else isSystemInDarkTheme()

            AppTheme(darkTheme = useDarkTheme) {
                LightAndNightApp(
                    sensorValue1 = sensorValue1,
                    sensorValue2 = sensorValue2,
                    isAutoBrightnessEnabled = isAutoBrightnessEnabled,
                    onAutoBrightnessToggle = { newValue ->
                        isAutoBrightnessEnabled = newValue
                    },
                    isAutoThemeEnabled = isAutoThemeEnabled,
                    onAutoThemeToggle = { newValue ->
                        isAutoThemeEnabled = newValue
                    }
                )
            }
        }

        targetSensor?.let { registerSensorListener(it) }
    }

    private fun registerSensorListener(sensor: Sensor?) {
        sensor?.let {
            sensorManager.unregisterListener(this)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Cria uma nova lista com os valores formatados
            val newSensorValues = it.values.mapIndexed { index, value ->
                "%.2f".format(value)
            }

            // Atualiza a lista reativa
            sensorValues.clear()
            sensorValues.addAll(newSensorValues)

            // Calcula o brilho apenas se o modo automático estiver ativado
            if (isAutoBrightnessEnabled) {
                val brightnessValue = calculateBrightness(it.values.firstOrNull() ?: 0f)
                setScreenBrightness(brightnessValue)
            }

            // Log dos valores formatados
            // println("Valores formatados do sensor: ${sensorValues.joinToString(", ")}")
        }
    }

    private fun calculateBrightness(sensorValue: Float): Int {
        val minBrightness = 15
        val maxBrightness = 255
        return ((sensorValue.coerceIn(0f, 100f) / 100f) * (maxBrightness - minBrightness) + minBrightness).toInt()
    }

    private fun setScreenBrightness(brightness: Int) {
        val contentResolver: ContentResolver = applicationContext.contentResolver
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        targetSensor?.let { registerSensorListener(it) }
    }

    private fun startForegroundService() {
        val intent = Intent(this, SensorForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

}

@Composable
fun LightAndNightApp(
    sensorValue1: String,
    sensorValue2: String,
    isAutoBrightnessEnabled: Boolean,
    onAutoBrightnessToggle: (Boolean) -> Unit,
    isAutoThemeEnabled: Boolean,
    onAutoThemeToggle: (Boolean) -> Unit
) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Light & Night") })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
//                // Switch para ativar/desativar o tema automático
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(text = "Tema automático", style = MaterialTheme.typography.bodyLarge)
//                    Switch(
//                        checked = isAutoThemeEnabled,
//                        onCheckedChange = { newValue ->
//                            onAutoThemeToggle(newValue)
//                        },
//                        colors = SwitchDefaults.colors(
//                            checkedThumbColor = Color.Blue,
//                            checkedTrackColor = Color.Blue.copy(alpha = 0.5f),
//                            uncheckedThumbColor = Color.Gray,
//                            uncheckedTrackColor = Color.LightGray
//                        )
//                    )
//                }

                // Texto informativo sobre os horários
//                if (isAutoThemeEnabled) {
//                    Text(
//                        text = "Modo claro: 6:00 - 17:30 | Modo escuro: 17:31 - 5:59",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                        modifier = Modifier.padding(top = 4.dp)
//                    )
//                } else {
//                    Text(
//                        text = "Seguindo o tema do sistema",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                        modifier = Modifier.padding(top = 4.dp)
//                    )
//                }

//                Spacer(modifier = Modifier.height(8.dp))

                // Switch para ativar/desativar o brilho automático
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Brilho automático", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isAutoBrightnessEnabled,
                        onCheckedChange = { newValue ->
                            onAutoBrightnessToggle(newValue)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Blue,
                            checkedTrackColor = Color.Blue.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Exibir os valores do sensor
                Text(
                    text = "Sensor 1: $sensorValue1",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sensor 2: $sensorValue2",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}