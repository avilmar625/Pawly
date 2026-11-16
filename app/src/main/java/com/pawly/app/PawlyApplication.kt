package com.pawly.app

import android.app.Application
import android.util.Log
import com.google.firebase.pnv.FirebasePhoneNumberVerification

/**
 * Initializes the free SIM-less PNV test session only in debug builds.
 * Real production verification is handled by Firebase PNV and may incur billing.
 */
class PawlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG || BuildConfig.FPNV_TEST_TOKEN.isBlank()) return
        runCatching {
            FirebasePhoneNumberVerification.getInstance().enableTestSession(BuildConfig.FPNV_TEST_TOKEN)
        }.onFailure {
            Log.w("PawlyPNV", "No se pudo activar la sesión de pruebas de verificación telefónica.", it)
        }
    }
}
