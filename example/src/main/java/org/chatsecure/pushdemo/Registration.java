package org.chatsecure.pushdemo;

import android.support.annotation.NonNull;

import org.chatsecure.pushsecure.pushsecure.PushSecureClient;
import org.chatsecure.pushsecure.pushsecure.response.Account;

import rx.Observable;
import timber.log.Timber;

public class Registration {

    /**
     * Wraps the process of ensuring this device is ready to request whitelist tokens
     * and send messages via the ChatSecure-Push API
     * <p>
     * Flow:
     * <ol>
     * <li>Get the current GCM token and get a ChatSecure-Push auth token in parallel. If no CSP auth token is available
     * via {@param provider}, one is requested from this class's client via {@param callback}</li>
     * <li>Register the GCM token with the ChatSecure-Push account obtained in prior step</li>
     * </ol>
     * <p>
     * Created by davidbrodsky on 7/7/15.
     */
    public static Observable<PushSecureClient> register(@NonNull Observable<String> getGcmToken,
                                                        @NonNull PushSecureClient client,
                                                        @NonNull DataProvider dataProvider,
                                                        @NonNull PushServiceRegistrationCredentialsProvider callback) {

        return Observable.zip(getGcmToken,
                              getOrCreateAuthToken(dataProvider, callback).doOnNext(client::setAccount),
                              (gcmToken, authToken) -> gcmToken)
                .doOnNext(gcmToken -> registerDevice(gcmToken, dataProvider, client))
                .map(gcmToken -> client);
    }

    /**
     * @return an observable for a ChatSecure-Push Authentication token
     * Either provided by storage or the client-provided callback
     */
    private static Observable<Account> getOrCreateAuthToken(DataProvider dataProvider, PushServiceRegistrationCredentialsProvider callback) {
        if (dataProvider.getPushSecureAuthToken() != null && dataProvider.getPushSecureUsername() != null)
            return Observable.just(
                    new Account(dataProvider.getPushSecureUsername(), dataProvider.getPushSecureAuthToken(), null));

        return callback.getChatSecurePushAccount()
                .doOnNext(account -> {
                    dataProvider.setPushSecureUsername(account.username);
                    dataProvider.setPushSecureAuthToken(account.token);
                });
    }

    /**
     * Registers the given GCM Token with the ChatSecure-Push server if necessary,
     * and saves it to the dataprovider if it was successfully sent
     */
    private static void registerDevice(String gcmToken, DataProvider dataProvider, PushSecureClient client) {
        if (dataProvider.getGcmToken() == null || !dataProvider.getGcmToken().equals(gcmToken)) {
            Timber.d("Registering GCM token with ChatSecure-Push");
            client.createDevice("whateverDevice", gcmToken, null)
                    .doOnNext(device -> dataProvider.setGcmToken(device.registrationId))
                    .toBlocking()
                    .single();
        } else {
            Timber.d("GCM token already registered with ChatSecure-Push");
        }
        // Do nothing. GCM Token already registered
    }

    public interface PushServiceRegistrationCredentialsProvider {
        /**
         * @return an observable for a newly created ChatSecure Push Account. Persisting the account is handled internally.
         */
        Observable<Account> getChatSecurePushAccount();
    }
}