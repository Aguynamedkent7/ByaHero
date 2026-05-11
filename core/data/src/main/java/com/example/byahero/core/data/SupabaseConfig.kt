package com.example.byahero.core.data

import android.util.Log
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.SettingsSessionManager
import com.russhwolf.settings.Settings

object SupabaseConfig {
    private const val TAG = "SupabaseConfig"

    val client by lazy {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        
        Log.d(TAG, "Initializing Supabase with URL: ${url.take(15)}...")
        
        if (url.isBlank() || key.isBlank()) {
            Log.e(TAG, "Supabase URL or Key is missing! Check your secrets configuration.")
        }

        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Postgrest)
            install(Auth) {
                sessionManager = SettingsSessionManager(Settings())
            }
            install(Realtime)
        }
    }
}
