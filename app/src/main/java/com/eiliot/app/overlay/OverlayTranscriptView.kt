package com.eiliot.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import kotlin.math.roundToInt

data class TranscriptActions(val open: () -> Unit, val off: () -> Unit, val close: () -> Unit,
    val talk: () -> Unit, val stop: () -> Unit, val more: (View) -> Unit,
    val send: (String) -> Boolean, val focus: () -> Unit, val draft: (String) -> Unit)

object OverlayPalette {
    val Surface = Color.rgb(16,26,42)
    val Text = Color.rgb(231,238,246)
    val Secondary = Color.rgb(182,196,213)
    val Accent = Color.rgb(83,213,208)
    val Attention = Color.rgb(245,188,105)
    val Error = Color.rgb(255,146,156)
}

/** The actual native overlay panel, also rendered and exercised by framework tests. */
class OverlayTranscriptView(context: Context, val mode: OverlayPanelMode, private val appearance: OverlayAppearance,
    private val actions: TranscriptActions, initialDraft: String) : LinearLayout(context) {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    val status: TextView
    val query: TextView
    val answer: TextView
    val scroll: ScrollView
    val input: EditText
    private val composer: LinearLayout
    private val review: LinearLayout
    private val reviewDetail: TextView
    private var editing = false
    private var allow: (() -> Unit)? = null
    private var deny: (() -> Unit)? = null
    val moreButton: TextView
    val openButton: TextView
    init {
        orientation = VERTICAL
        setPadding(dp(12),dp(8),dp(12),dp(8)); elevation = dp(8).toFloat()
        background = GradientDrawable().apply { setColor(OverlayPalette.Surface); cornerRadius=dp(24).toFloat();setStroke(dp(1),OverlayPalette.Accent) }
        val head=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        val more=button("•••","More ELLIOT actions") { }
        more.setOnClickListener { actions.more(more) };moreButton=more
        openButton=button("ELLIOT ↗", "Open ELLIOT") { actions.open() }
        head.addView(openButton, LayoutParams(0,dp(48),1f))
        if(mode==OverlayPanelMode.INPUT)head.addView(moreButton,LayoutParams(dp(48),dp(48)))
        head.addView(button("Off", "Turn ELLIOT off", OverlayPalette.Error) { actions.off() }, LayoutParams(dp(64),dp(48)))
        head.addView(button("×", "Close transcript") { actions.close() }, LayoutParams(dp(48),dp(48)))
        addView(head)
        status=text("Here with you",14f,OverlayPalette.Accent).apply { maxLines=1; ellipsize=TextUtils.TruncateAt.END;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        if(mode==OverlayPanelMode.FULL)addView(status,LayoutParams(-1,-2))
        query=text("",14f,OverlayPalette.Secondary).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END }
        answer=text("Talk or type a message.",16f).apply { setTextIsSelectable(true);setLineSpacing(dp(3).toFloat(),1f) }
        scroll=ScrollView(context).apply { addView(answer);isFillViewport=false }
        review=LinearLayout(context).apply { orientation=VERTICAL;visibility=GONE }
        reviewDetail=text("",16f,OverlayPalette.Attention)
        review.addView(ScrollView(context).apply { addView(reviewDetail) },LayoutParams(-1,0,1f))
        val choices=LinearLayout(context)
        choices.addView(button("Allow","Allow this optional review step") { allow?.invoke() },LayoutParams(0,dp(48),1f))
        choices.addView(button("Decline","Decline this step",OverlayPalette.Attention) { deny?.invoke() },LayoutParams(0,dp(48),1f))
        review.addView(choices)
        input=EditText(context).apply {
            hint="Message ELLIOT";setTextColor(OverlayPalette.Text);setHintTextColor(OverlayPalette.Secondary)
            textSize=16f;isSingleLine=true;imeOptions=EditorInfo.IME_ACTION_SEND;minHeight=dp(48)
            setPadding(dp(8),dp(4),dp(8),dp(4))
            background=GradientDrawable().apply { setColor(Color.rgb(6,11,19));cornerRadius=dp(12).toFloat() }
            setText(initialDraft)
        }
        composer=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        composer.addView(input,LayoutParams(0,dp(48),1f))
        composer.addView(button("↑","Send message") { send() },LayoutParams(dp(48),dp(48)))
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { if(!editing) actions.draft(s.toString()) }
            override fun afterTextChanged(s:Editable?)=Unit
        })
        input.setOnFocusChangeListener { _,hasFocus -> if(hasFocus) actions.focus() }
        input.setOnTouchListener { _,event -> if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN) actions.focus(); false }
        input.setOnEditorActionListener { _,action,event ->
            if(action==EditorInfo.IME_ACTION_SEND || (event?.keyCode==android.view.KeyEvent.KEYCODE_ENTER && event.action==android.view.KeyEvent.ACTION_UP)) { send();true } else false
        }
        if(mode==OverlayPanelMode.FULL) {
            if(resources.configuration.fontScale<=1.3f) addView(query,LayoutParams(-1,-2))
            addView(scroll,LayoutParams(-1,0,1f));addView(review,LayoutParams(-1,0,1f))
            addView(composer,LayoutParams(-1,dp(48)))
        } else if(mode==OverlayPanelMode.INPUT) addView(composer,LayoutParams(-1,dp(48)))
        else status.text="Open ELLIOT to type or read more"
        val controls=LinearLayout(context)
        controls.addView(button("Talk","Talk to ELLIOT") { actions.talk() },LayoutParams(0,dp(48),1f))
        controls.addView(button("Stop","Stop response",OverlayPalette.Attention) { actions.stop() },LayoutParams(0,dp(48),1f))
        if(mode!=OverlayPanelMode.INPUT) {
            controls.addView(moreButton,LayoutParams(dp(48),dp(48)))
            addView(controls,LayoutParams(-1,dp(48)))
        }
    }
    private fun text(value:String,size:Float,color:Int=OverlayPalette.Text) = TextView(context).apply {
        text=value;textSize=size;setTextColor(color);includeFontPadding=false;setPadding(dp(3),dp(4),dp(3),dp(4))
    }
    private fun button(value:String,label:String,color:Int=OverlayPalette.Accent,action:()->Unit)=text(value,14f,color).apply {
        contentDescription=label;gravity=Gravity.CENTER;minWidth=dp(48);minHeight=dp(48);isFocusable=true;isClickable=true
        maxLines=1;ellipsize=TextUtils.TruncateAt.END
        background=GradientDrawable().apply { setColor(Color.argb(18,83,213,208));cornerRadius=dp(10).toFloat() }
        setOnClickListener { if(appearance.haptics) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);action() }
        accessibilityDelegate=object:View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host:View,info:android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info);info.className="android.widget.Button"
            }
        }
    }
    private fun send() { val value=input.text.toString();if(actions.send(value)) bindDraft("") }
    fun bindDraft(value:String) {
        if(input.text.toString()==value)return
        editing=true;input.setText(value);input.setSelection(value.length);editing=false
    }
    fun showReview(title:String?,detail:String?,allow:()->Unit,deny:()->Unit) {
        this.allow=allow;this.deny=deny
        if(mode!=OverlayPanelMode.FULL) { if(title!=null) { openButton.text="Review ↗";openButton.contentDescription="Open ELLIOT. Review: $title. More has Allow or Decline." };return }
        val pending=title!=null
        review.visibility=if(pending) VISIBLE else GONE
        scroll.visibility=if(pending) GONE else VISIBLE
        composer.visibility=if(pending) GONE else VISIBLE
        query.visibility=if(pending) GONE else VISIBLE
        reviewDetail.text=if(pending) "$title\n${detail.orEmpty()}" else ""
    }
}
