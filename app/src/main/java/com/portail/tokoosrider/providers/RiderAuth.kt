package com.portail.tokoosrider.providers

import android.content.Context
import android.preference.PreferenceManager
import com.portail.tokoosrider.models.User
import com.google.gson.Gson
import com.portail.tokoosrider.models.Rider

class RiderAuth (contxt: Context) {
    private val context = contxt
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPreferencesEditor = sharedPreferences.edit()

    private val SESSION_RIDER_KEY = "session_rider"

    public fun saveRider(rider: String) {
        sharedPreferencesEditor.putString(SESSION_RIDER_KEY, rider)
        sharedPreferencesEditor.apply()
        sharedPreferencesEditor.commit()
    }

    public fun getRider() : Rider? {
        val rider = sharedPreferences.getString(SESSION_RIDER_KEY, "")
        return if (rider != "") {
            Gson().fromJson(rider, Rider::class.java)
        } else { null }
    }

    public fun isLoggedIn(): Boolean = this.getRider() != null
}