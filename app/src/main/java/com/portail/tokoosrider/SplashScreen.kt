package com.portail.tokoosrider

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.portail.tokoosrider.providers.RiderAuth
import kotlinx.android.synthetic.main.activity_splash_screen.*

class SplashScreen : AppCompatActivity() {

    private lateinit var riderAuth: RiderAuth

    private var handler: Handler? = null
    private val runnable: Runnable = Runnable {
        if(riderAuth.isLoggedIn()){
            startActivity(Intent(this@SplashScreen, TokoosRider::class.java))
        } else { startActivity(Intent(this@SplashScreen, SignIn::class.java)) }
    }

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        riderAuth = RiderAuth(this@SplashScreen)

        val spanText = SpannableString("TokoosRider".toUpperCase())
        spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorPrimary)), 0, 6, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorAccent)), 6, spanText.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        app_name.text = spanText

        handler = Handler()
        handler?.postDelayed(runnable, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(runnable)
    }
}
