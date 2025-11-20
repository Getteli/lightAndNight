package com.getteli.lightandnight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat

class SensorForegroundService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var targetSensor: Sensor? = null

    // Estado compartilhado para controlar o brilho automático
    companion object {
        var isAutoBrightnessEnabled = QuickSettingsTileService.isAutoBrightnessEnabled
    }

    override fun onCreate() {
        super.onCreate()
        // println("SensorForegroundService criado.")

        // Inicializa o SensorManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Obtém o sensor específico
        val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
        targetSensor = sensorList.find { it.name == "back_als_str" || it.type == 33171095 }

        // Registra o listener do sensor
        targetSensor?.let { registerSensorListener(it) }

        // Cria o canal de notificação
        createNotificationChannel()

        // Exibe a notificação para manter o serviço em primeiro plano
        val notification = NotificationCompat.Builder(this, "sensor_service_channel")
            .setContentTitle("Sensor de Brilho Automático")
            .setContentText("Monitorando sensores...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // println("SensorForegroundService iniciado.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // println("SensorForegroundService destruído.")
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun registerSensorListener(sensor: Sensor) {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val sensorValue = it.values.firstOrNull() ?: 0f

            // Ajusta o brilho apenas se o modo automático estiver ativado
            if (isAutoBrightnessEnabled.value)
            {
                val brightnessValue = calculateBrightness(sensorValue)
                setScreenBrightness(brightnessValue)
            }

            // println("Valor do sensor: $sensorValue")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sensor_service_channel",
                "Sensor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal para o serviço de monitoramento de sensores"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            // println("Canal de notificação criado.")
        }
    }
}