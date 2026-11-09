package br.com.voz99.data

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voz99_preferences", Context.MODE_PRIVATE)

    var homeAddress: String
        get() = prefs.getString("home_address", "") ?: ""
        set(value) { prefs.edit().putString("home_address", value.trim()).apply() }

    var defaultPayment: String
        get() = prefs.getString("default_payment", "Dinheiro") ?: "Dinheiro"
        set(value) { prefs.edit().putString("default_payment", value.trim()).apply() }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) { prefs.edit().putString("user_name", value.trim()).apply() }
}
