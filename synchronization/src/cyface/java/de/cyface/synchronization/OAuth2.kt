package de.cyface.synchronization

import android.content.Context
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthState.AuthStateAction
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService

class OAuth2(context: Context): Auth {

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

    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        mStateManager.current.performActionWithFreshTokens(mAuthService, action)
    }
}