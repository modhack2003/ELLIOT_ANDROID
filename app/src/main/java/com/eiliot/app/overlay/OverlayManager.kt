package com.eiliot.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import com.eiliot.app.MainActivity
import com.eiliot.app.capabilities.CapabilityRegistry
import com.eiliot.app.core.*
import com.eiliot.app.tools.ActionApprovals
import com.eiliot.app.tools.PendingAction
import com.eiliot.app.ui.OutputFormat
import com.eiliot.app.voice.VoiceIo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

/** Small, separately focusable windows. All placement uses measured safe bounds. */
object OverlayManager {
    private data class HudState(val conversation: ConversationState, val speaking: Boolean, val listening: Boolean, val transcript: String, val action: PendingAction?)
    private val main=Handler(Looper.getMainLooper())
    private var ctx:Context?=null
    private var wm:WindowManager?=null
    private var layout:OverlayLayoutStore?=null
    private var currentAppearance:OverlayAppearance?=null
    private var controller:OverlayWindowController?=null
    private var orb:ArcCoreView?=null
    private var orbParams:WindowManager.LayoutParams?=null
    private var panel:OverlayTranscriptView?=null
    private var panelParams:WindowManager.LayoutParams?=null
    private var caption:LinearLayout?=null
    private var captionText:TextView?=null
    private var captionStatus:TextView?=null
    private var captionParams:WindowManager.LayoutParams?=null
    private var popup:PopupMenu?=null
    private var collect:Job?=null
    private var preferences:Job?=null
    private var drafts:Job?=null
    private var profile=""
    private var anchor=OverlayAnchor()
    private var viewport:OverlayViewport?=null
    private var panelSignature=""
    private var dragging=false
    private var typing=false
    private val windowSpecs=mutableMapOf<View,String>()
    private var gesture:OrbGesture?=null
    private var pendingState=OrbState.IDLE
    private var settingsHandoff=false
    private var follow=true
    private var scrollY=0
    private var showingScreen=false
    private var lastTurn=0L
    private var lastCaptionReply=""
    private var target="";private var index=0
    private var captionTarget="";private var captionIndex=0
    private var screenCallback:Runnable?=null
    private val relayout=Runnable { placeWindows() }
    private val dismissCaption=Runnable { removeCaption() }
    private val writer=object:Runnable {
        override fun run() {
            val p=panel?:return
            if(!SentinelRuntime.alive() || privateNow())return
            index=OutputFormat.advance(target,index,if(target.length-index>150)20 else 3)
            p.answer.text=target.take(index)
            if(follow)p.scroll.post { if(panel===p && follow)p.scroll.fullScroll(View.FOCUS_DOWN) }
            if(index<target.length)main.postDelayed(this,18)
        }
    }
    private val captionWriter=object:Runnable {
        override fun run() {
            val view=captionText?:return
            if(!SentinelRuntime.alive() || privateNow())return
            captionIndex=OutputFormat.advance(captionTarget,captionIndex,5);view.text=captionTarget.take(captionIndex)
            if(captionIndex<captionTarget.length)main.postDelayed(this,20)
        }
    }
    private fun dp(v:Int)=((ctx?.resources?.displayMetrics?.density?:1f)*v).roundToInt()
    private fun appearance()=layout?.appearance()?:OverlayAppearance()
    private fun instantText()= !appearance().typing || ctx?.let { layout?.reducedMotion(it) }==true
    private fun onMain(action:()->Unit) { if(Looper.myLooper()==Looper.getMainLooper())action()else main.post(action) }
    private fun privateNow():Boolean {
        val c=ctx?:return true
        return c.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked || !c.getSystemService(android.os.PowerManager::class.java).isInteractive
    }
    private fun params(width:Int,height:Int)=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply {
        gravity=Gravity.TOP or Gravity.LEFT
        softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if(android.os.Build.VERSION.SDK_INT>=28)layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    fun canDraw(context:Context)=Settings.canDrawOverlays(context)
    fun isShowing()=orb!=null
    fun show(context:Context)=onMain {
        if(settingsHandoff && !SentinelRuntime.foreground)return@onMain
        if(SentinelRuntime.foreground)settingsHandoff=false
        if(!SentinelRuntime.alive() || !canDraw(context))return@onMain
        if(orb!=null) { placeWindows();return@onMain }
        ctx=ContextThemeWrapper(context.applicationContext,android.R.style.Theme_Material_NoActionBar)
        wm=context.getSystemService(WindowManager::class.java)
        layout=OverlayLayoutStore(context)
        controller=OverlayWindowController(ctx!!) { main.removeCallbacks(relayout);main.post(relayout) }
        val a=appearance();val p=params(dp(a.sizeDp),dp(a.sizeDp))
        val view=ArcCoreView(ctx!!).apply {
            setState(pendingState);setPresentation(a.motion,a.haptics)
            isClickable=true;isLongClickable=true;isFocusable=true
            setOnClickListener { if(panel==null)openPanel()else collapse() }
            setOnLongClickListener { if(!privateNow())EiliotBrain.tapToTalk(context);true }
        }
        gesture=OrbGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat(),ViewConfiguration.getLongPressTimeout().toLong())
        var downX=0f;var downY=0f;var startX=0;var startY=0
        view.setOnTouchListener { v,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if(event.pointerCount!=1)return@setOnTouchListener false
                    downX=event.rawX;downY=event.rawY;startX=p.x;startY=p.y;dragging=false
                    gesture?.down(downX,downY,event.eventTime);true
                }
                MotionEvent.ACTION_MOVE -> {
                    if(event.pointerCount!=1) { gesture?.cancel();dragging=false;placeWindows();return@setOnTouchListener true }
                    if(gesture?.move(event.rawX,event.rawY)==true) {
                        dragging=true
                        viewport?.usable?.let { bounds -> OverlayGeometry.clamp(bounds,startX+(event.rawX-downX).toInt(),startY+(event.rawY-downY).toInt(),p.width,p.height)?.let {
                            p.x=it.left;p.y=it.top;updateWindow(view,p);placePanel();placeCaption()
                        } }
                    };true
                }
                MotionEvent.ACTION_UP -> {
                    val result=gesture?.end(event.eventTime);dragging=false
                    when(result) {
                        OrbGesture.Result.DRAG -> viewport?.let { saveAnchor(OverlayGeometry.anchor(it.stable,orbRect())) }
                        OrbGesture.Result.TAP -> v.performClick()
                        OrbGesture.Result.TALK -> v.performLongClick()
                        else -> Unit
                    }
                    placeWindows();true
                }
                MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> { gesture?.cancel();dragging=false;placeWindows();true }
                else -> false
            }
        }
        orb=view;orbParams=p
        if(!addWindow(view,p)) { hide();return@onMain }
        controller?.observe(view);controller?.start();installActions(view);placeWindows()
        collect=SentinelRuntime.scope.launch {
            combine(EiliotBrain.state,VoiceIo.isSpeaking,VoiceIo.isListening,VoiceIo.transcript,ActionApprovals.pending) { s,speaking,listening,transcript,action -> HudState(s,speaking,listening,transcript,action) }.collect { h ->
                if(!SentinelRuntime.alive())return@collect
                if(privateNow()) { closePanel();removeCaption();return@collect }
                if(h.action!=null && panel==null)openPanel()
                if(h.conversation.turnId!=lastTurn) { showingScreen=false;follow=true;scrollY=0;lastTurn=h.conversation.turnId }
                update(h.conversation);review(h.action);updateCaption(h.conversation,h.speaking,h.listening,h.transcript)
            }
        }
        preferences=SentinelRuntime.scope.launch { layout?.changes?.collect {
            val next=appearance()
            if(next!=currentAppearance) { currentAppearance=next;applyAppearance() }
            else if(profile.isNotBlank() && !dragging) {
                viewport?.let { anchor=layout!!.anchor(profile,it.stable,dp(next.sizeDp));placeWindows() }
            }
        } }
        drafts=SentinelRuntime.scope.launch { EiliotBrain.draft.collect { panel?.bindDraft(it) } }
    }
    private fun installActions(view:View) {
        ViewCompat.addAccessibilityAction(view,"Talk to ELLIOT") { _,_->if(!privateNow())EiliotBrain.tapToTalk(ctx!!);true }
        ViewCompat.addAccessibilityAction(view,"Open transcript") { _,_->openPanel();true }
        ViewCompat.addAccessibilityAction(view,"Move orb left") { _,_->move(DockEdge.LEFT);true }
        ViewCompat.addAccessibilityAction(view,"Move orb right") { _,_->move(DockEdge.RIGHT);true }
        ViewCompat.addAccessibilityAction(view,"Move orb up") { _,_->moveVertical(-.15f);true }
        ViewCompat.addAccessibilityAction(view,"Move orb down") { _,_->moveVertical(.15f);true }
        ViewCompat.addAccessibilityAction(view,"Turn ELLIOT off") { _,_->SentinelRuntime.stop();true }
    }
    private fun applyAppearance() {
        if(orb==null)return
        val a=appearance();orb?.setPresentation(a.motion,a.haptics)
        if(!a.captions)removeCaption()
        if(panel!=null)rebuildPanel()
        placeWindows()
        if(instantText()) { main.removeCallbacks(writer);main.removeCallbacks(captionWriter);index=target.length;panel?.answer?.text=target;captionIndex=captionTarget.length;captionText?.text=captionTarget }
    }
    private fun orbRect():OverlayRect { val p=orbParams?:return OverlayRect(0,0,0,0);return OverlayRect(p.x,p.y,p.x+p.width,p.y+p.height) }
    private fun saveAnchor(value:OverlayAnchor) { anchor=value.sanitized();layout?.saveAnchor(profile,anchor) }
    private fun editAnchor(transform:(OverlayAnchor)->OverlayAnchor) {
        if(layout!=null && profile.isNotBlank()) { saveAnchor(transform(anchor));placeWindows();return }
        val c=SentinelRuntime.context
        val store=OverlayLayoutStore(c)
        try {
            val v=OverlayWindowController(c) { }.viewport()
            val size=(store.appearance().sizeDp*v.density).roundToInt()
            store.saveAnchor(v.profile,transform(store.anchor(v.profile,v.stable,size)))
        } finally { store.close() }
    }
    fun move(edge:DockEdge)=onMain { editAnchor { it.copy(edge=edge) } }
    fun moveVertical(delta:Float)=onMain { editAnchor { it.copy(fraction=it.fraction+delta) } }
    fun resetPosition()=onMain { editAnchor { OverlayAnchor() } }
    fun handleLayoutCommand(command:OverlayLayoutCommand):String {
        check(SentinelRuntime.alive()) { "Resume ELLIOT first" }
        when(command) {
            OverlayLayoutCommand.LEFT -> editAnchor { it.copy(edge=DockEdge.LEFT) }
            OverlayLayoutCommand.RIGHT -> editAnchor { it.copy(edge=DockEdge.RIGHT) }
            OverlayLayoutCommand.RESET -> editAnchor { OverlayAnchor() }
            OverlayLayoutCommand.CAPTIONS_OFF,OverlayLayoutCommand.CAPTIONS_ON -> {
                val store=layout?:OverlayLayoutStore(SentinelRuntime.context)
                try { store.save(store.appearance().copy(captions=command==OverlayLayoutCommand.CAPTIONS_ON)) }
                finally { if(store!==layout)store.close() }
            }
        }
        return when(command) {
            OverlayLayoutCommand.LEFT -> "Left orb placement saved."
            OverlayLayoutCommand.RIGHT -> "Right orb placement saved."
            OverlayLayoutCommand.RESET -> "Orb position reset."
            OverlayLayoutCommand.CAPTIONS_OFF -> "Floating captions are hidden. Voice settings are unchanged."
            OverlayLayoutCommand.CAPTIONS_ON -> "Floating captions are enabled for eligible replies."
        } + if(orb==null) " Enable the floating companion and overlay access in Settings to see it." else ""
    }
    private fun placeWindows() {
        val c=ctx?:return;val view=orb?:return
        if(!SentinelRuntime.alive() || !canDraw(c)) { hide();return }
        val next=controller?.viewport()?:return
        val a=appearance()
        viewport?.let { old -> if(old.stable!=next.stable || old.density!=next.density || old.fontScale!=next.fontScale) { gesture?.cancel();dragging=false } }
        if(profile!=next.profile) { gesture?.cancel();dragging=false;profile=next.profile;anchor=layout!!.anchor(profile,next.stable,dp(a.sizeDp)) }
        val mode=OverlayGeometry.panel(next.usable,orbRect(),next.density,next.fontScale,typing)?.mode
        val signature="${next.usable.width}:${next.fontScale}:${next.density}:$mode"
        viewport=next
        val rect=OverlayGeometry.orb(next.usable,dp(a.sizeDp),anchor)
        if(rect==null) { view.visibility=View.INVISIBLE;closePanel();removeCaption();return }
        view.visibility=View.VISIBLE
        orbParams?.let { p ->
            p.width=rect.width;p.height=rect.height;p.alpha=a.opacity
            if(!dragging) { p.x=rect.left;p.y=rect.top };updateWindow(view,p)
        }
        view.setPresentation(a.motion,a.haptics);view.setPaused(next.locked || !next.interactive)
        if(next.locked || !next.interactive) { popup?.dismiss();closePanel();removeCaption();return }
        if(panel!=null && signature!=panelSignature) { panelSignature=signature;rebuildPanel() } else placePanel()
        placeCaption()
    }
    fun openPanel()=onMain {
        if(panel!=null || orb==null || !SentinelRuntime.alive() || privateNow())return@onMain
        val c=ctx?:return@onMain;val v=controller?.viewport()?:return@onMain
        val placement=OverlayGeometry.panel(v.usable,orbRect(),v.density,v.fontScale,typing)
        if(placement==null) { openApp();EiliotBrain.notice("Open ELLIOT for more room. The orb stays available.");return@onMain }
        removeCaption()
        val root=OverlayTranscriptView(c,placement.mode,appearance(),TranscriptActions(
            open={openApp()},off={SentinelRuntime.stop()},close={collapse()},talk={releaseKeyboard();EiliotBrain.tapToTalk(c)},stop={EiliotBrain.cancelCurrent()},
            more={showMore(it)},send={ value -> if(EiliotBrain.send(value,"overlay")) { EiliotBrain.draft.value="";releaseKeyboard();true } else false },
            focus={focusInput()},draft={EiliotBrain.draft.value=it}),EiliotBrain.draft.value)
        root.scroll.setOnTouchListener { _,e->if(e.actionMasked==MotionEvent.ACTION_DOWN)follow=false;false }
        root.scroll.setOnScrollChangeListener { _:View,_:Int,y:Int,_:Int,_:Int ->
            scrollY=y;if(root.scroll.getChildAt(0).height-root.scroll.height-y<=dp(16))follow=true
        }
        panel=root;panelParams=params(placement.rect.width,placement.rect.height).apply { x=placement.rect.left;y=placement.rect.top }
        panelSignature="${v.usable.width}:${v.fontScale}:${v.density}:${placement.mode}"
        if(!addWindow(root,panelParams!!)) { panel=null;panelParams=null;return@onMain }
        controller?.observe(root);update(EiliotBrain.state.value);review(ActionApprovals.pending.value)
        if(showingScreen)showScreen()
        val saved=scrollY
        if(!follow)root.scroll.post { if(panel===root)root.scroll.scrollTo(0,saved) }
        if(typing)root.post { if(panel===root)focusInput() }
    }
    private fun rebuildPanel() { if(panel==null)return;val keepInput=typing && !privateNow();closePanel(keepInput);openPanel() }
    private fun placePanel() {
        val root=panel?:return;val p=panelParams?:return;val v=viewport?:return
        val placement=OverlayGeometry.panel(v.usable,orbRect(),v.density,v.fontScale,typing)
        if(placement==null) { closePanel();EiliotBrain.notice("More room is available in the app");return }
        if(root.mode!=placement.mode) { rebuildPanel();return }
        p.width=placement.rect.width;p.height=placement.rect.height;p.x=placement.rect.left;p.y=placement.rect.top;updateWindow(root,p)
    }
    private fun review(action:PendingAction?) {
        panel?.showReview(action?.title,action?.detail,
            allow={releaseKeyboard();action?.let { ActionApprovals.resolve(it.id,true) }},deny={action?.let { ActionApprovals.resolve(it.id,false) }})
    }
    private fun focusInput() {
        if(privateNow() || !SentinelRuntime.alive())return
        val root=panel?:return;val p=panelParams?:return
        typing=true
        if(p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE!=0) {
            p.flags=p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv();updateWindow(root,p)
            root.input.requestFocus()
            root.post { if(panel===root && SentinelRuntime.alive())ctx?.getSystemService(InputMethodManager::class.java)?.showSoftInput(root.input,InputMethodManager.SHOW_IMPLICIT) }
        }
    }
    private fun releaseKeyboard() {
        typing=false
        val root=panel?:return
        ctx?.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(root.input.windowToken,0)
        root.input.clearFocus()
        panelParams?.let { it.flags=it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;updateWindow(root,it) }
        controller?.clearKeyboard()
    }
    fun prepareForPhoneAction()=onMain { popup?.dismiss();releaseKeyboard() }
    fun suspendForSettings()=onMain { settingsHandoff=true;hide() }
    private fun openApp() { val c=ctx?:return;closePanel();c.startActivity(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    private fun showMore(anchorView:View) {
        val c=ctx?:return;popup?.dismiss()
        val menu=PopupMenu(c,anchorView)
        fun item(label:String,action:()->Unit) { menu.menu.add(label).setOnMenuItemClickListener { if(SentinelRuntime.alive())action();true } }
        item("Stop response") { EiliotBrain.cancelCurrent() }
        item("Talk to ELLIOT") { EiliotBrain.tapToTalk(c) }
        item("Read screen") {
            prepareForPhoneAction();screenCallback?.let(main::removeCallbacks)
            val epoch=SentinelRuntime.epoch
            val r=Runnable { if(SentinelRuntime.alive(epoch) && panel!=null) { ScreenReader.capture();showScreen() } }
            screenCallback=r;main.postDelayed(r,200)
        }
        item("Explain selected screen") { EiliotBrain.explainScreen() }
        item("Latest reply") { follow=true;panel?.scroll?.fullScroll(View.FOCUS_DOWN) }
        item("Move orb left") { move(DockEdge.LEFT) };item("Move orb right") { move(DockEdge.RIGHT) }
        item("Move orb up") { moveVertical(-.15f) };item("Move orb down") { moveVertical(.15f) }
        item("Reset position") { resetPosition() }
        item(if(appearance().captions)"Hide captions" else "Show captions") { layout?.save(appearance().copy(captions=!appearance().captions)) }
        item("Appearance in Settings") { openApp() }
        ActionApprovals.pending.value?.let { a -> item("Allow this optional step") { releaseKeyboard();ActionApprovals.resolve(a.id,true) };item("Decline step") { ActionApprovals.resolve(a.id,false) } }
        popup=menu
        runCatching { menu.show() }.onFailure { EiliotBrain.notice("Open Settings in ELLIOT for these options") }
    }
    private fun showScreen() {
        val s=ScreenReader.snapshot.value;showingScreen=s!=null
        panel?.status?.text=ScreenReader.message.value;panel?.query?.text=s?.app.orEmpty()
        main.removeCallbacks(writer);target=OutputFormat.prefix(s?.text.orEmpty(),8000);index=target.length;panel?.answer?.text=target
    }
    private fun update(s:ConversationState) {
        val root=panel?:return
        if(showingScreen && !s.busy && s.turnId==lastTurn)return
        val presence=CapabilityRegistry.currentSession()
        root.status.text=if(root.mode!=OverlayPanelMode.FULL)"${presence.phase.label} · Open app for more" else "${presence.phase.label} · ${s.error?.let(com.eiliot.app.ui.FriendlyStatus::error)?:s.status}"
        if(root.mode!=OverlayPanelMode.FULL) {
            root.openButton.text="${if(s.error!=null) "Needs attention" else presence.phase.label} ↗"
            root.openButton.contentDescription="Open ELLIOT. ${presence.phase.label}. ${s.error?.let(com.eiliot.app.ui.FriendlyStatus::error)?:s.status}"
        }
        root.query.text=if(s.query.isBlank())"" else "You: ${s.query.take(160)}"
        val full=OutputFormat.plain(s.reply)
        val next=(OutputFormat.prefix(full,8000)+if(full.length>8000)"\n\nOpen ELLIOT for the full response." else "").ifBlank { if(s.busy)"Thinking…" else "Talk or type a message." }
        if(!next.startsWith(target) || next.length<index)index=0
        target=next;main.removeCallbacks(writer)
        if(!s.busy || instantText() || root.mode!=OverlayPanelMode.FULL) { index=target.length;root.answer.text=target;if(follow)root.scroll.post { if(panel===root && follow)root.scroll.fullScroll(View.FOCUS_DOWN) } }
        else main.post(writer)
    }
    private fun updateCaption(s:ConversationState,speaking:Boolean,listening:Boolean=VoiceIo.isListening.value,transcript:String=VoiceIo.transcript.value) {
        if(panel!=null || orb==null || !appearance().captions || privateNow()) { removeCaption();return }
        val changed=s.reply.isNotBlank() && s.reply!=lastCaptionReply
        if(!s.busy && !speaking && !listening && !changed) {
            if(caption!=null) { main.removeCallbacks(dismissCaption);main.postDelayed(dismissCaption,appearance().timeoutSeconds*1000L) }
            return
        }
        val c=ctx?:return;lastCaptionReply=s.reply;main.removeCallbacks(dismissCaption)
        if(caption==null) {
            val root=LinearLayout(c).apply {
                orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));minimumHeight=dp(48)
                background=GradientDrawable().apply { setColor(OverlayPalette.Surface);cornerRadius=dp(18).toFloat();setStroke(dp(1),OverlayPalette.Accent) }
                isClickable=true;isFocusable=true;setOnClickListener { openPanel() }
                contentDescription="ELLIOT live captions. Open transcript"
            }
            captionStatus=TextView(c).apply { textSize=12f;setTextColor(OverlayPalette.Accent);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END }
            captionText=TextView(c).apply { textSize=appearance().captionSp.toFloat();setTextColor(OverlayPalette.Text);maxLines=4;ellipsize=android.text.TextUtils.TruncateAt.END }
            root.addView(captionStatus);root.addView(captionText)
            caption=root;captionParams=params(dp(276),dp(120))
            if(!addWindow(root,captionParams!!)) { caption=null;captionParams=null;return }
            controller?.observe(root)
        }
        captionStatus?.text="ELLIOT · ${CapabilityRegistry.currentSession().phase.label}"
        val next=when {
            listening -> transcript.ifBlank { "I'm listening…" }
            s.reply.isBlank() -> (if(s.query.isNotBlank())"You: ${OutputFormat.prefix(s.query,150)}\n" else "")+s.status
            else -> (if(s.reply.length>300)"…" else "")+OutputFormat.tail(OutputFormat.plain(s.reply),300)
        }
        if(!next.startsWith(captionTarget))captionIndex=0
        captionTarget=next;main.removeCallbacks(captionWriter)
        caption?.contentDescription="ELLIOT. ${CapabilityRegistry.currentSession().phase.label}. $next. Open transcript."
        if(instantText()) { captionIndex=next.length;captionText?.text=next } else main.post(captionWriter)
        placeCaption()
        if(!s.busy && !speaking && !listening)main.postDelayed(dismissCaption,appearance().timeoutSeconds*1000L)
    }
    private fun placeCaption() {
        val root=caption?:return;val p=captionParams?:return;val v=viewport?:return
        val width=minOf(dp(276),v.usable.width)
        val maxHeight=(v.usable.height*.20f).toInt()
        val lineHeight=captionText?.lineHeight?:dp(20)
        val heading=captionStatus?.lineHeight?:dp(16)
        val lines=((maxHeight-heading-dp(16))/lineHeight.coerceAtLeast(1)).coerceIn(0,4)
        if(lines==0 || width<dp(120)) { removeCaption();return }
        captionText?.maxLines=lines
        // Measure against the full caption content, so typing never expands the window across the screen.
        val old=captionText?.text;captionText?.text=captionTarget
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST))
        captionText?.text=old
        val height=maxOf(dp(48),root.measuredHeight)
        val rect=OverlayGeometry.adjacent(v.usable,orbRect(),width,height,dp(8))
        if(rect==null) { removeCaption();return }
        p.width=rect.width;p.height=rect.height;p.x=rect.left;p.y=rect.top;updateWindow(root,p)
    }
    private fun spec(p:WindowManager.LayoutParams)="${p.x}:${p.y}:${p.width}:${p.height}:${p.flags}:${p.alpha}"
    private fun addWindow(view:View,p:WindowManager.LayoutParams):Boolean=runCatching { checkNotNull(wm).addView(view,p);windowSpecs[view]=spec(p);true }.getOrElse { SentinelRuntime.status.value="Floating window unavailable; check overlay access";false }
    private fun updateWindow(view:View,p:WindowManager.LayoutParams) {
        val next=spec(p);if(windowSpecs[view]==next)return
        runCatching { checkNotNull(wm).updateViewLayout(view,p);windowSpecs[view]=next }.onFailure {
            val epoch=SentinelRuntime.epoch;val original=orb
            main.removeCallbacks(relayout);main.post { if(SentinelRuntime.epoch==epoch && orb===original)hide() }
        }
    }
    private fun removeWindow(view:View) { controller?.unobserve(view);windowSpecs.remove(view);view.animate().cancel();runCatching { wm?.removeViewImmediate(view) } }
    private fun removeCaption() {
        main.removeCallbacks(captionWriter);main.removeCallbacks(dismissCaption)
        caption?.let(::removeWindow);caption=null;captionText=null;captionStatus=null;captionParams=null;captionTarget="";captionIndex=0
    }
    private fun closePanel(preserveKeyboard:Boolean=false) {
        popup?.dismiss();popup=null
        val root=panel
        if(root!=null) { scrollY=root.scroll.scrollY;if(!preserveKeyboard)releaseKeyboard();removeWindow(root) }
        else if(!preserveKeyboard) { typing=false;controller?.clearKeyboard() }
        main.removeCallbacks(writer);screenCallback?.let(main::removeCallbacks);screenCallback=null
        panel=null;panelParams=null;target="";index=0
    }
    private fun collapse() {
        closePanel()
        if(SentinelRuntime.alive() && !privateNow()) { lastCaptionReply="";updateCaption(EiliotBrain.state.value,VoiceIo.isSpeaking.value) }
    }
    fun hide()=onMain {
        collect?.cancel();collect=null;preferences?.cancel();preferences=null;drafts?.cancel();drafts=null
        main.removeCallbacks(relayout);gesture?.cancel();gesture=null;dragging=false
        closePanel();removeCaption();controller?.close();controller=null;orb?.let(::removeWindow);orb=null;orbParams=null
        layout?.close();layout=null;currentAppearance=null;wm=null;ctx=null;viewport=null;profile="";panelSignature="";typing=false;windowSpecs.clear()
    }
    fun setOrbState(state:OrbState)=onMain { pendingState=state;orb?.setState(state) }
    fun orbState():OrbState?=orb?.getState()
    fun setOrbAmplitude(level:Float)=onMain { orb?.setAmplitude(level) }
    fun setOrbPaused(paused:Boolean)=onMain {
        orb?.setPaused(paused || privateNow())
        if(paused || privateNow()) { closePanel();removeCaption() } else { placeWindows();updateCaption(EiliotBrain.state.value,VoiceIo.isSpeaking.value) }
    }
    fun requestPermission(context:Context) { suspendForSettings();context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    internal fun windowRects():List<OverlayRect> = listOfNotNull(orbParams,panelParams,captionParams).map { OverlayRect(it.x,it.y,it.x+it.width,it.y+it.height) }
    internal fun hasPrivateWindows()=panel!=null || caption!=null
}
