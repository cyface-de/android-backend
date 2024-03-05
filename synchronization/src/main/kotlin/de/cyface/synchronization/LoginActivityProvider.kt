package de.cyface.synchronization

import android.app.Activity

interface LoginActivityProvider {
    fun getLoginActivity(): Class<out Activity?>?
}