package com.eiliot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { EiliotTheme { WithOverlayAppearance {
            var setupComplete by remember { mutableStateOf(SentinelRuntime.store.isInitialSetupComplete) }
            if(setupComplete)EiliotShell()
            else ProvideHolo { Box(Modifier.fillMaxSize()) { CosmicBackdrop(); OnboardingScreen { setupComplete=true } } }
        } } }
    }
    override fun onResume() { super.onResume(); com.eiliot.app.capabilities.CapabilityRegistry.refresh() }
}

private data class Destination(val label:String,val icon:ImageVector)
private val destinations=listOf(Destination("Companion",EiliotIcons.Orb),Destination("Vault",EiliotIcons.Vault),Destination("Settings",EiliotIcons.Tune))

@Composable internal fun EiliotShell() = ProvideHolo {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var direction by remember { mutableFloatStateOf(1f) }
    fun go(next:Int) { if(next!=tab) { direction=if(next>tab)1f else -1f;tab=next } }
    val reduced=rememberReducedMotion()
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    Box(Modifier.fillMaxSize().background(EiliotColors.Void)) {
        CosmicBackdrop()
        Column(Modifier.fillMaxSize()) {
            PowerHeader()
            AnimatedContent(tab,Modifier.weight(1f),label="page",transitionSpec={
                if(reduced) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                else (fadeIn(tween(380,90))+scaleIn(EiliotMotion.tweenEnter(),initialScale=.9f)) togetherWith
                    (fadeOut(tween(220))+scaleOut(tween(300),targetScale=.94f)) using SizeTransform(clip=false)
            }) { page ->
                // Pages swing on a vertical hinge, like panels of a projected display.
                val swing by transition.animateFloat({ tween(520,easing=EiliotMotion.easeOutExpo) },label="swing") { phase ->
                    if(reduced) 0f else when(phase) { EnterExitState.PreEnter->26f*direction; EnterExitState.Visible->0f; EnterExitState.PostExit->-20f*direction }
                }
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY=swing;cameraDistance=12f*density }) {
                    when (page) {
                        0 -> HomeScreen(onCapabilities = { go(2) })
                        1 -> VaultScreen(onSettings={ go(2) })
                        else -> SettingsScreen()
                    }
                }
            }
            AnimatedVisibility(!keyboard,enter=slideInVertically { it }+fadeIn(),exit=slideOutVertically { it }+fadeOut()) {
                HoloDock(tab,::go)
            }
        }
        ActionApprovalDialog()
    }
}

/**
 * Floating glass dock. A light pool slides between destinations on a soft
 * spring, and the selected glyph rises out of the glass with a glow beneath it.
 */
@Composable private fun HoloDock(tab:Int,onSelect:(Int)->Unit) {
    val shape=RoundedCornerShape(30.dp)
    val slot by animateFloatAsState(tab.toFloat(),EiliotMotion.springLift(),label="dockSlot")
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=16.dp,end=16.dp,bottom=10.dp,top=2.dp)
        .shadow(18.dp,shape,clip=false,ambientColor=EiliotColors.Cyan.copy(alpha=.25f),spotColor=EiliotColors.Violet.copy(alpha=.45f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xE6111A3A),Color(0xF2060A1A))))
        .holoSurface(EiliotColors.Cyan,brackets=false)
        .border(1.dp,Brush.horizontalGradient(listOf(EiliotColors.Cyan.copy(alpha=.55f),EiliotColors.Violet.copy(alpha=.25f),EiliotColors.Magenta.copy(alpha=.45f))),shape)
        .heightIn(min=64.dp)) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(1f/destinations.size)
                .graphicsLayer { translationX=slot*size.width }
                .padding(horizontal=10.dp,vertical=7.dp)
                .drawBehind {
                    val r=size.height/2f
                    drawRoundRect(Brush.verticalGradient(listOf(EiliotColors.Cyan.copy(alpha=.24f),EiliotColors.Violet.copy(alpha=.10f))),cornerRadius=androidx.compose.ui.geometry.CornerRadius(r,r))
                    drawCircle(Brush.radialGradient(listOf(EiliotColors.Cyan.copy(alpha=.38f),Color.Transparent),Offset(size.width/2f,size.height*.36f),size.height*.7f),size.height*.7f,Offset(size.width/2f,size.height*.36f))
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent,EiliotColors.Cyan,Color.Transparent)),Offset(size.width*.25f,0f),Size(size.width*.5f,2.dp.toPx()))
                })
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            destinations.forEachIndexed { index,destination -> DockItem(destination,tab==index,{onSelect(index)},Modifier.weight(1f)) }
        }
    }
}

@Composable private fun DockItem(destination:Destination,selected:Boolean,onClick:()->Unit,modifier:Modifier) {
    val view=LocalView.current
    val lift by animateFloatAsState(if(selected)1f else 0f,EiliotMotion.springLift(),label="lift")
    val interaction=remember { MutableInteractionSource() }
    Column(modifier.heightIn(min=64.dp)
        .clickable(interaction,LocalIndication.current,role=Role.Tab) { HudHaptics.tap(view);onClick() }
        .semantics { this.selected=selected }
        .padding(vertical=8.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Icon(destination.icon,null,tint=androidx.compose.ui.graphics.lerp(EiliotColors.TextSecondary,EiliotColors.Cyan,lift),
            modifier=Modifier.size(24.dp).graphicsLayer {
                translationY=-3.dp.toPx()*lift
                scaleX=1f+.14f*lift;scaleY=scaleX
            })
        Text(destination.label,style=MaterialTheme.typography.labelSmall.copy(letterSpacing=if(LocalDensity.current.fontScale>1.3f)0.sp else 1.2.sp),
            color=androidx.compose.ui.graphics.lerp(EiliotColors.TextMuted,EiliotColors.TextPrimary,lift),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
    }
}
