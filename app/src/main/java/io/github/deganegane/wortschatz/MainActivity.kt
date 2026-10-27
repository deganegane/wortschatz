package io.github.deganegane.wortschatz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.deganegane.wortschatz.ui.AppScreen
import io.github.deganegane.wortschatz.ui.WortschatzTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** Set by the reminder notification; consumed once the word list has loaded. */
    private var pendingReview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            handleIntent(intent)
        }
        addOnNewIntentListener { handleIntent(it) }

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            if (pendingReview && !state.loading) {
                pendingReview = false
                if (state.session == null) vm.startSession()
            }
            WortschatzTheme(dynamicColor = state.settings.dynamicColor, themeMode = state.settings.themeMode) {
                AppScreen(vm = vm, state = state)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.cancelListening()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDay()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_REVIEW, false) == true) {
            intent.removeExtra(EXTRA_START_REVIEW)
            if (vm.state.value.loading) {
                pendingReview = true
            } else if (vm.state.value.session == null) {
                vm.startSession()
            }
        }
    }

    companion object {
        const val EXTRA_START_REVIEW = "start_review"
    }
}
