package com.trioscope.chameleon.activity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.trioscope.chameleon.R;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/24/15.
 */
@Slf4j
public class PreferencesActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("Creating preferences activity");

        SettingsFragment frag = new SettingsFragment();
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .commit();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(frag);
    }

    /**
     * This fragment shows the preferences for the first header.
     */
    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Make sure default values are applied.  In a real app, you would
            // want this in a shared function that is used to retrieve the
            // SharedPreferences wherever they are needed.
            PreferenceManager.setDefaultValues(getActivity(),
                    R.xml.preferences, false);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_stream_resolution).equals(key)) {
                Preference connectionPref = findPreference(key);
                // Set summary to be the user-description for the selected value
                if (connectionPref != null){
                    connectionPref.setSummary(sharedPreferences.getString(key, ""));
                }
            } else if (getString(R.string.pref_user_name_key).equals(key)) {

                Preference connectionPref = findPreference(key);
                // Set summary to be the user-description for the selected value
                if (connectionPref != null) {
                    connectionPref.setSummary(sharedPreferences.getString(key, ""));
                }
            } else {
                log.info("Preferences changed from {} not {}", key, getString(R.string.pref_stream_resolution));
            }
            log.info("Preferences changed for {}", key);
        }
    }
}
