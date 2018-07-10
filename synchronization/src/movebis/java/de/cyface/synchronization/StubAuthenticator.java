package de.cyface.synchronization;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;

/**
 * Stub class for an authenticator. This is necessary since an authenticator is a requirement for a synchronisation
 * adapter. But since we are not supporting any user accounts the implementation is mostly empty. Fur further details
 * visit the <a href="https://developer.android.com/training/sync-adapters/creating-authenticator.html">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class StubAuthenticator extends AbstractAccountAuthenticator {

    /**
     * Creates a new completely initialized <code>StubAuthenticator</code>.
     *
     * @param context The Android context for the authenticator.
     */
    StubAuthenticator(final @NonNull Context context) {
        super(context);
    }

    @Override
    public Bundle editProperties(final @NonNull AccountAuthenticatorResponse response, final @NonNull String accountType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle addAccount(final @NonNull AccountAuthenticatorResponse response, final @NonNull String accountType, final @NonNull String authTokenType,
            String[] requiredFeatures, Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle confirmCredentials(final @NonNull AccountAuthenticatorResponse response, final @NonNull Account account, final @NonNull Bundle options)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle getAuthToken(final @NonNull AccountAuthenticatorResponse response, final @NonNull Account account, final @NonNull String authTokenType,
            Bundle options) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAuthTokenLabel(final @NonNull String authTokenType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle updateCredentials(final @NonNull AccountAuthenticatorResponse response, final @NonNull Account account, final @NonNull String authTokenType,
            Bundle options) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle hasFeatures(final @NonNull AccountAuthenticatorResponse response, final @NonNull Account account, final @NonNull String[] features)
            throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }
}
