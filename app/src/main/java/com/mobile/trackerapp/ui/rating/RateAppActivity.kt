package com.mobile.trackerapp.ui.rating

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R
import com.mobile.trackerapp.network.TrackerBackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RateAppActivity : AppCompatActivity() {
    private val navy = Color.rgb(10, 18, 45); private val green = Color.rgb(0, 170, 70); private val orange = Color.rgb(255, 174, 0); private val muted = Color.rgb(125, 137, 160)
    private lateinit var card: LinearLayout; private var rating = 0; private var submitting = false
    override fun onCreate(state: Bundle?) { super.onCreate(state); window.setBackgroundDrawableResource(android.R.color.transparent); showRatingStart() }
    private fun base(): LinearLayout { val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(16),dp(16),dp(16),dp(16)); setBackgroundColor(0x99000000.toInt()) }; card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(20),dp(12),dp(20),dp(22)); background=round(Color.WHITE,28f) }; val close=TextView(this).apply { text="×"; textSize=32f; setTextColor(0xFF7D89A5.toInt()); gravity=Gravity.CENTER; setOnClickListener { finish() } }; card.addView(close, LinearLayout.LayoutParams(-1,dp(42)).apply { gravity=Gravity.END }); root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { gravity=Gravity.CENTER }); setContentView(root); return card }
    private fun showRatingStart() { val c=base(); c.addView(illustration(R.drawable.rate_initial)); c.addView(title("Enjoying the app?")); c.addView(sub("Your feedback helps us improve.")); val stars=stars(); c.addView(stars); c.addView(sub("Tap a star to rate")); val rate=button("Rate us",rating > 0); c.addView(rate); c.addView(link("Maybe later") { finish() }); rate.setOnClickListener { if (rating > 0) showRatingResult() } }
    private fun showRatingResult() { val c=base(); c.addView(illustration(if(rating<=2) R.drawable.rate_sorry else if(rating==3) R.drawable.rate_thanks else R.drawable.rate_glad)); c.addView(title(if(rating<=2) "Sorry to hear that! 😔" else if(rating==3) "Thanks for your feedback!" else "Glad you're enjoying it! 🎉")); c.addView(sub(if(rating<=2) "What can we improve?" else if(rating==3) "What could make it better?" else "Would you like to rate us\non Google Play?")); c.addView(stars()); if(rating>=4) { val b=button("Rate on Google Play",true); c.addView(b); b.setOnClickListener { openPlay() } } else { val b=button("Give feedback",true); c.addView(b); b.setOnClickListener { showFeedback() } }; c.addView(link("Maybe later") { finish() }) }
    private fun showFeedback() { val c=base(); c.addView(illustration(R.drawable.rate_feedback)); c.addView(title("Share your feedback")); c.addView(sub("Let us know how we can improve.")); val input=EditText(this).apply { hint="Write your feedback here..."; gravity=Gravity.TOP; minLines=5; maxLines=8; setSingleLine(false); setPadding(dp(12),dp(12),dp(12),dp(12)); background=round(0xFFF3FAF4.toInt(),14f); filters=arrayOf(InputFilter.LengthFilter(500)); contentDescription="Feedback" }; c.addView(input, LinearLayout.LayoutParams(-1,dp(150)).apply{topMargin=dp(16)}); val count=sub("0/500"); c.addView(count,LinearLayout.LayoutParams(-1,dp(28)).apply{gravity=Gravity.END}); input.addTextChangedListener(SimpleWatcher { count.text="${input.length()}/500" }); val submit=button("Submit",true); c.addView(submit); submit.setOnClickListener { val text=input.text.toString().trim(); if(text.isEmpty()){input.error="Please enter feedback"; input.requestFocus(); return@setOnClickListener}; if(submitting)return@setOnClickListener; submitting=true; submit.isEnabled=false; submit.text="Saving…"; (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(input.windowToken,0); CoroutineScope(Dispatchers.Main).launch { runCatching { TrackerBackendClient(this@RateAppActivity).register(); TrackerBackendClient(this@RateAppActivity).submitFeedback(rating,text) }.onSuccess { showSuccess() }.onFailure { submitting=false; submit.isEnabled=true; submit.text="Submit"; Toast.makeText(this@RateAppActivity,"Could not save feedback. Try again.",Toast.LENGTH_LONG).show() } } } }
    private fun showSuccess() { val c=base(); c.addView(illustration(R.drawable.rate_feedback)); c.addView(title("Thanks for your feedback!")); c.addView(sub("We appreciate your help in making the app better.")) }
    private fun stars(): LinearLayout { val row=LinearLayout(this).apply{gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL}; repeat(5){i-> val s=TextView(this).apply{text=if(i<rating)"★" else "☆";textSize=38f;setTextColor(if(i<rating)orange else 0xFFB8D9BD.toInt());gravity=Gravity.CENTER;setOnClickListener{rating=i+1;showRatingResult()}}; row.addView(s,LinearLayout.LayoutParams(dp(50),dp(58)))}; return row }
    private fun title(t:String)=TextView(this).apply{text=t;textSize=20f;setTextColor(navy);setTypeface(null,1);gravity=Gravity.CENTER;setPadding(0,dp(10),0,dp(4))}
    private fun sub(t:String)=TextView(this).apply{text=t;textSize=14f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(0,dp(5),0,dp(5))}
    private fun emoji(t:String,size:Int)=TextView(this).apply{text=t;textSize=size.toFloat();gravity=Gravity.CENTER;setPadding(0,dp(4),0,dp(4))}
    private fun illustration(resource: Int) = ImageView(this).apply { setImageResource(resource); adjustViewBounds=true; scaleType=ImageView.ScaleType.CENTER_INSIDE; layoutParams=LinearLayout.LayoutParams(-1,dp(104)).apply { bottomMargin=dp(4) } }
    private fun button(t:String,enabled:Boolean)=TextView(this).apply{text=t;textSize=15f;setTypeface(null,1);gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=round(if(enabled)green else 0xFFBDBDBD.toInt(),30f);isEnabled=enabled;setPadding(0,dp(13),0,dp(13));layoutParams=LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(12)}}
    private fun link(t:String,click:()->Unit)=TextView(this).apply{text=t;textSize=14f;setTextColor(green);gravity=Gravity.CENTER;setPadding(0,dp(14),0,dp(4));setOnClickListener{click()}}
    private fun round(color:Int,r:Float)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(r.toInt()).toFloat()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun openPlay(){ val id=packageName; val intent=Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")); runCatching{startActivity(intent)}.onFailure{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=$id")))}; finish() }
    private class SimpleWatcher(val changed:(String)->Unit): android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int)=changed(s?.toString().orEmpty());override fun afterTextChanged(e:android.text.Editable?){} }
}
