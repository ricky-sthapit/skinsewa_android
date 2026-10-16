package org.swasthaconnect

import android.app.Application
import org.swasthaconnect.core.designsystem.ThemePreferences
import org.swasthaconnect.data.auth.AuthApi
import org.swasthaconnect.data.auth.SecureTokenStore

class SkinSewaApplication : Application() {
    lateinit var tokenStore: SecureTokenStore
        private set
    lateinit var authApi: AuthApi
        private set
    lateinit var themePreferences: ThemePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = SecureTokenStore(getSharedPreferences("secure_session", MODE_PRIVATE))
        authApi = AuthApi.create(BuildConfig.API_BASE_URL, tokenStore)
        themePreferences = ThemePreferences(this)
    }
}
