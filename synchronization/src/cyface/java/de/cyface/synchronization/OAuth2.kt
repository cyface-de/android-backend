package de.cyface.synchronization

import android.content.Context
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService

class OAuth2(context: Context) {

    private var mAuthService: AuthorizationService
    private var mStateManager: AuthStateManager
    private var mConfiguration: Configuration

    init {
        // Authorization
        mStateManager = AuthStateManager.getInstance(context)
        mConfiguration = Configuration.getInstance(context)
        val config = Configuration.getInstance(context)
        /*if (config.hasConfigurationChanged()) {
            //throw IllegalArgumentException("config changed (SyncAdapter)")
            Toast.makeText(context, "Ignoring: config changed (SyncAdapter)", Toast.LENGTH_SHORT).show()
            //Handler().postDelayed({signOut()}, 2000)
            //return
        }*/
        mAuthService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
        )
    }

    fun performActionWithFreshTokens(action: AuthState.AuthStateAction) {
        mStateManager.current.performActionWithFreshTokens(mAuthService, action)
    }
}