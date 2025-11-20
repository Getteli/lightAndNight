package com.getteli.lightandnight

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.mutableStateOf

class QuickSettingsTileService : TileService() {

    // Estados compartilhados entre o serviço e a MainActivity
    companion object {
        var isAutoBrightnessEnabled = mutableStateOf(false)
        var isAutoThemeEnabled = mutableStateOf(false) // Não será usado pelo tile
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // Alterna APENAS o estado do brilho automático
        isAutoBrightnessEnabled.value = !isAutoBrightnessEnabled.value

        // Imprime no Logcat o novo estado
        // ("Brilho Automático Ativado: ${isAutoBrightnessEnabled.value}")

        // Atualiza o estado do tile
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile
        if (tile != null) {
            // Define o estado do tile com base APENAS no brilho automático
            tile.state = if (isAutoBrightnessEnabled.value) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            tile.label = if (isAutoBrightnessEnabled.value) {
                "Brilho Automático"
            } else {
                "Brilho Automático"
            }
            tile.updateTile()

            // Imprime no Logcat o estado atualizado do tile
            // println("Tile atualizado - Estado: ${tile.state}, Label: ${tile.label}")
        }
    }
}