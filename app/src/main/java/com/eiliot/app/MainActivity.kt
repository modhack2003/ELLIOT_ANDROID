package com.eiliot.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.core.EiliotBrain
import com.eiliot.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { EiliotTheme { WithOverlayAppearance {
            var setupComplete by remember { mutableStateOf(SentinelRuntime.store.isInitialSetupComplete) }
            if(setupComplete)EiliotShell()else OnboardingScreen { setupComplete=true }
        } } }
    }
    override fun onResume() { super.onResume(); com.eiliot.app.capabilities.CapabilityRegistry.refresh() }
}
@Composable internal fun EiliotShell() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF040811), Color(0xFF111A30), Color(0xFF050912))))) {
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Color(0xF0091120), tonalElevation = 0.dp) {
                val labels = listOf("Companion", "Vault", "Settings")
                val icons = listOf(Icons.Default.Home, Icons.Default.Info, Icons.Default.Settings)
                labels.forEachIndexed { index, text ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], text) }, label = { Text(text,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = EiliotColors.Cyan, selectedTextColor = EiliotColors.Cyan, indicatorColor = Color(0x2521D7E8), unselectedIconColor = EiliotColors.TextSecondary, unselectedTextColor = EiliotColors.TextSecondary))
                }
            }
        }) { insets -> Column(Modifier.fillMaxSize().padding(insets)) {
            PowerHeader()
            Box(Modifier.weight(1f)) { when (tab) {
                0 -> HomeScreen(onCapabilities = { tab = 2 })
                1 -> VaultScreen(onSettings={tab=2})
                else -> SettingsScreen()
            } }
        } }
        ActionApprovalDialog()
    }
}
