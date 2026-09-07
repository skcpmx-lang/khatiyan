package com.shohan.khatiyan

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.shohan.khatiyan.presentation.KhatiyanMainApp
import com.shohan.khatiyan.presentation.onboarding.OnboardingScreen
import com.shohan.khatiyan.presentation.lock.LockScreen
import com.shohan.khatiyan.ui.theme.KhatiyanTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * The single activity of খতিয়ান (Navigation Component hosts every screen).
 * Responsibilities: splash, light-only theming, onboarding gate, app-lock gate
 * and the optional FLAG_SECURE "no content on lock screen / recents" setting.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            KhatiyanTheme { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as KhatiyanApp).container
        lifecycleScope.launch {
            container.appLock.onResume(container.settings.current())
        }
    }

    override fun onPause() {
        super.onPause()
        (application as KhatiyanApp).container.appLock.onBackground(SystemClock.elapsedRealtime())
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as KhatiyanApp
    val container = app.container
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: run {
        Box(Modifier.fillMaxSize()) // splash stays until the first settings value arrives
        return
    }

    // Optional screenshot/recents/lock-screen privacy (Phase 20/35). Default on.
    SideEffect {
        val activity = context as? MainActivity
        val flag = if (s.secureScreen) {
            WindowManager.LayoutParams.FLAG_SECURE
        } else {
            0
        }
        activity?.window?.setFlags(flag, WindowManager.LayoutParams.FLAG_SECURE)
    }

    when {
        !s.onboarded -> OnboardingScreen(container)
        s.appLockEnabled && container.appLock.locked.value -> LockScreen(container)
        else -> KhatiyanMainApp()
    }
}
