package io.github.deganegane.wortschatz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.UiState

private enum class Tab(val label: String, val icon: Int) {
    TODAY("Danas", R.drawable.ic_today),
    TEST("Test", R.drawable.ic_quiz),
    READING("Čitanje", R.drawable.ic_read),
    DICTIONARY("Rječnik", R.drawable.ic_book)
}

private enum class Screen { MAIN, REVIEW, SHADOW, GRAMMAR, SETTINGS }

@Composable
fun AppScreen(vm: AppViewModel, state: UiState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openStory by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val screen = when {
        state.session != null -> Screen.REVIEW
        state.shadowing != null -> Screen.SHADOW
        state.grammar != null -> Screen.GRAMMAR
        showSettings -> Screen.SETTINGS
        else -> Screen.MAIN
    }

    BackHandler(enabled = screen == Screen.SETTINGS) { showSettings = false }
    BackHandler(enabled = screen == Screen.MAIN && tab != 0 && openStory == null) { tab = 0 }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut()
        },
        label = "screen"
    ) { target ->
        when (target) {
            Screen.REVIEW -> ReviewScreen(vm = vm, state = state)
            Screen.SHADOW -> ShadowingScreen(vm = vm, state = state)
            Screen.GRAMMAR -> GrammarScreen(vm = vm, state = state)
            Screen.SETTINGS -> SettingsScreen(vm = vm, state = state, onBack = { showSettings = false })
            Screen.MAIN -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(painterResource(t.icon), contentDescription = null) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (Tab.entries[tab]) {
                        Tab.TODAY -> TodayTab(
                            vm = vm,
                            state = state,
                            onOpenTest = { tab = Tab.TEST.ordinal },
                            onOpenSettings = { showSettings = true }
                        )
                        Tab.TEST -> TestTab(vm = vm, state = state, onStartLearning = {
                            tab = Tab.TODAY.ordinal
                            vm.startSession()
                        })
                        Tab.READING -> ReadingTab(vm = vm, state = state, openStory = openStory, onOpen = { openStory = it })
                        Tab.DICTIONARY -> DictionaryTab(vm = vm, state = state)
                    }
                }
            }
        }
    }
}
