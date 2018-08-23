/*
 * Copyright (C) 2017 Shobhit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.preferences;

import android.accounts.Account;
import android.app.Fragment;
import android.content.ContentResolver;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;

import static org.odk.collect.android.preferences.PreferenceKeys.KEY_ANALYTICS;
import static org.odk.collect.android.syncadapter.SyncUtils.AUTHORITY;
import static org.odk.collect.android.syncadapter.SyncUtils.CreateSyncAccount;

public class SyncPreferences extends BasePreferenceFragment implements Preference.OnPreferenceClickListener{

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sync_preferences);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar.setTitle(R.string.user_and_device_identity_title);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (toolbar != null) {
            toolbar.setTitle(R.string.general_preferences);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals("force_update")) {
            Bundle settingsBundle = new Bundle();
            Account account = CreateSyncAccount(Collect.getInstance().getApplicationContext());
            ContentResolver.requestSync(account, AUTHORITY, settingsBundle);
        }
        return true;
    }

}
