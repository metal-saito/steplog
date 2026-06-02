package com.cellomsai.steplog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.cellomsai.steplog.ui.navigation.StepLogBottomBar
import com.cellomsai.steplog.ui.navigation.StepLogNavHost
import com.cellomsai.steplog.ui.screens.onboarding.OnboardingScreen
import com.cellomsai.steplog.ui.theme.AppTheme
import com.cellomsai.steplog.ui.theme.StepLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val appTheme by viewModel.appTheme.collectAsState(initial = AppTheme.SYSTEM)
            val onboardingDone by viewModel.onboardingDone.collectAsState()

            // Hold splash screen until DataStore has resolved the onboarding state
            splashScreen.setKeepOnScreenCondition { onboardingDone == null }

            StepLogTheme(appTheme = appTheme) {
                AnimatedContent(
                    targetState = onboardingDone,
                    transitionSpec = {
                        fadeIn(tween(500)) togetherWith fadeOut(tween(300))
                    },
                    label = "onboarding_transition",
                ) { done ->
                    when (done) {
                        null -> {} // Loading — splash screen is still visible
                        false -> OnboardingScreen(onComplete = viewModel::completeOnboarding)
                        true -> {
                            val navController = rememberNavController()
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                bottomBar = { StepLogBottomBar(navController) },
                            ) { innerPadding ->
                                StepLogNavHost(
                                    navController = navController,
                                    modifier = Modifier
                                        .padding(innerPadding)
                                        .consumeWindowInsets(innerPadding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
