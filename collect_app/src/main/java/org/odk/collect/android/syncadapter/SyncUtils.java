package org.odk.collect.android.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.odk.collect.android.application.Collect;

import static android.content.Context.ACCOUNT_SERVICE;
import static android.provider.ContactsContract.Directory.ACCOUNT_TYPE;
import static org.odk.collect.android.syncadapter.SyncAdapter.TAG;

/**
 * Created by jyri on 14/02/18.
 */

public class SyncUtils {

    // Constants
    // The authority for the sync adapter's content provider
    public static final String AUTHORITY = "org.odk.collect.android.provider.odk.sync";
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "org.odk.collect.android.syncadapter";
    // The account name
    public static final String ACCOUNT = "sync_account";

    /**
     * Create a new dummy account for the sync adapter
     *
     * @param context The application context
     */
    public static Account CreateSyncAccount(Context context) {
        // Create the account type and default account
        Account newAccount = new Account(
                ACCOUNT, ACCOUNT_TYPE);
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(
                        ACCOUNT_SERVICE);
        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call context.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */
            return newAccount;
        } else {
            Log.w(TAG, "No " + ACCOUNT + " account generated");
        }
        return newAccount;
    }

    public static void InitSync(Account account){

        int pollFrequency = 60;
        Bundle settingsBundle = new Bundle();

        Log.i(TAG, "Initializing SyncAdapter");

        try {
            ContentResolver.setIsSyncable(account, AUTHORITY, 1);
            // Inform the system that this account is eligible for auto sync when the network is up
            ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
            // Recommend a schedule for automatic synchronization. The system may modify this based
            // on other scheduled syncs and network utilization.
            ContentResolver.addPeriodicSync(
                    account, AUTHORITY, Bundle.EMPTY, pollFrequency * 60);

            settingsBundle.putBoolean(
                    ContentResolver.SYNC_EXTRAS_MANUAL, true);
            settingsBundle.putBoolean(
                    ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

            ContentResolver.requestSync(account, AUTHORITY, settingsBundle);
        } catch (NullPointerException e) {
            Log.e(TAG, "Error when initializing sync: " + e.getMessage());
        }
    }
}
