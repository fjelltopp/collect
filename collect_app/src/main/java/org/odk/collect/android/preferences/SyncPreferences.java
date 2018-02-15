/*
 * Copyright 2017 Shobhit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.odk.collect.android.preferences;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.annotation.Nullable;
import android.view.View;

import org.odk.collect.android.R;

import static org.odk.collect.android.preferences.PreferenceKeys.KEY_ALLOW_FORM_SYNC;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_PROTOCOL;


public class SyncPreferences extends ServerPreferencesFragment implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sync_preferences);

        initSyncPrefs();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar.setTitle(R.string.sync_preferences);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (toolbar != null) {
            toolbar.setTitle(R.string.general_preferences);
        }
    }

    private void initSyncPrefs() {
        final ListPreference sync = (ListPreference) findPreference(KEY_ALLOW_FORM_SYNC);

        if (sync == null) {
            return;
        }

        sync.setSummary(sync.getEntry());
        sync.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int index = ((ListPreference) preference).findIndexOfValue(newValue.toString());
                String entry = (String) ((ListPreference) preference).getEntries()[index];
                preference.setSummary(entry);
                return true;
            }
        });
    }
}
