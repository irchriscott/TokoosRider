package com.portail.tokoosrider

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.portail.tokoosrider.models.Rider
import com.portail.tokoosrider.models.ServerResponse
import com.portail.tokoosrider.providers.RiderAuth
import com.portail.tokoosrider.utils.Routes
import com.pusher.pushnotifications.PushNotifications
import kotlinx.android.synthetic.main.activity_sign_in.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception

@Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class SignIn : AppCompatActivity() {

    private val RC_SIGN_IN = 1
    lateinit var mGoogleSignInClient: GoogleSignInClient

    private lateinit var progressDialog: ProgressDialog

    private var handler: Handler? = null
    private val runnable: Runnable = Runnable {
        bike.visibility = View.GONE
        car.visibility = View.VISIBLE
        val rightToLeftAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.car_right_to_left)
        car.startAnimation(rightToLeftAnimation)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        PushNotifications.start(applicationContext, "1a848f70-1cfd-4103-b5a3-668162332a26")
        PushNotifications.addDeviceInterest("hello")

        val leftToRightAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bike_left_to_right)
        bike.startAnimation(leftToRightAnimation)
        car.startAnimation(leftToRightAnimation)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this@SignIn, gso)

        handler = Handler()
        handler?.postDelayed(runnable, 5000)

        progressDialog = ProgressDialog(this@SignIn)
        progressDialog.setProgressStyle(R.style.ProgressBar)
        progressDialog.setCanceledOnTouchOutside(false)

        google_button.setOnClickListener {
            mGoogleSignInClient.signOut()
            google_button.isClickable = false
            google_button.alpha = 0.4f
            progressDialog.setMessage("Veillez Patienter")
            progressDialog.show()
            val signInIntent = mGoogleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(runnable)
    }

    override fun onBackPressed() {}

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val url = Routes().riderAuth

            val client = OkHttpClient()
            val jsonObject = JSONObject()
            val mediaType = MediaType.parse("application/json")

            try { jsonObject.put("email", account?.email) }
            catch (e: JSONException) {}

            val form = RequestBody.create(mediaType, jsonObject.toString())
            val request = Request.Builder()
                .addHeader("Content-Type", "application/json")
                .url(url)
                .post(form)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        google_button.isClickable = true
                        google_button.alpha = 1.0f
                        Toast.makeText(this@SignIn, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseText = response.body()!!.string()
                    runOnUiThread {
                        google_button.isClickable = true
                        google_button.alpha = 1.0f
                        progressDialog.dismiss()
                        if(response.code() == 200) {
                            try {
                                val rider = Gson().fromJson(responseText, Rider::class.java)
                                val riderAuth = RiderAuth(this@SignIn)
                                riderAuth.saveRider(Gson().toJson(rider))
                                startActivity(Intent(this@SignIn, TokoosRider::class.java))
                            } catch (e: Exception){}
                        } else {
                            try {
                                val serverResponse = Gson().fromJson(responseText, ServerResponse::class.java)
                                error_text.visibility = View.VISIBLE
                                error_text.text = serverResponse.message
                            } catch (e: Exception){}
                        }
                    }
                }
            })

        } catch (e: ApiException) {
            google_button.isClickable = true
            google_button.alpha = 1.0f
            progressDialog.dismiss()
            Toast.makeText(this@SignIn, "Google Sign In Failed", Toast.LENGTH_LONG).show()
        }
    }
}
