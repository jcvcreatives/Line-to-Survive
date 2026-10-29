package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.SoundEffectManager
import com.example.data.ProgressRepository
import com.example.data.local.AppDatabase
import com.example.data.local.GameRepository
import com.example.game.GameViewModel
import com.example.ui.DrawToSurviveScreen
import com.example.ui.theme.DrawToSurviveTheme

class MainActivity : ComponentActivity() {
  private var soundEffectManager: SoundEffectManager? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val progressRepository = ProgressRepository(applicationContext)
    val appDatabase = AppDatabase.getDatabase(applicationContext)
    val gameRepository = GameRepository(appDatabase.gameDao())
    val manager = SoundEffectManager(
      context = applicationContext,
      initialEnabled = progressRepository.isSoundEnabled()
    )
    soundEffectManager = manager

    setContent {
      DrawToSurviveTheme {
        val gameViewModel: GameViewModel = viewModel(
          factory = GameViewModel.Factory(
            progressRepository = progressRepository,
            soundManager = manager,
            gameRepository = gameRepository
          )
        )
        DrawToSurviveScreen(viewModel = gameViewModel)
      }
    }
  }

  override fun onPause() {
    super.onPause()
    soundEffectManager?.pauseBackgroundMusic()
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
      soundEffectManager?.release()
    }
  }
}
