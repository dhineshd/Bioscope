package com.trioscope.chameleon.activity;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.support.v7.app.AppCompatActivity;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/24/15.
 */
@Slf4j
public class PreferencesActivity extends AppCompatActivity {
    private SettingsFragment settingsFragment;
    private ImageButton minimizeSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("Creating preferences activity");
        setContentView(R.layout.activity_preference_activity);


        minimizeSettings = (ImageButton) findViewById(R.id.minimize_settings);

        minimizeSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        settingsFragment = new SettingsFragment();

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.settings_frag_contents, settingsFragment)
                .commit();


    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(settingsFragment);
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(settingsFragment);
        super.onPause();
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
            PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
            //Initialize the values
            Preference connectionPref = findPreference(getString(R.string.pref_user_name_key));
            connectionPref.setSummary(settings.getString(getString(R.string.pref_user_name_key), ""));

            Preference versionNumberPref = findPreference(getString(R.string.pref_version_number_key));
            versionNumberPref.setSummary(getVersionName());

        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_user_name_key).equals(key)) {
                Preference connectionPref = findPreference(key);

                // Set summary to be the user-description for the selected value
                if (connectionPref != null) {
                    connectionPref.setSummary(sharedPreferences.getString(key, ""));
                }
            } else if (getString(R.string.pref_codec_key).equals(key)) {
                final SwitchPreference connectionPref = (SwitchPreference) findPreference(key);
                boolean isOn = connectionPref.isChecked();
                log.info("User changed the OpenH Setting, current value is {}", isOn);

                if (isOn) {
                    log.info("Turning on OpenH264 without question");
                } else {
                    log.info("Asking user for confirmation when disabling OpenH264");
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Disable OpenH264")
                            .setMessage("Do you really want to disable OpenH264? OpenH264 codec is required for merging videos.")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, null)
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    if (connectionPref != null) {
                                        connectionPref.setChecked(true);
                                    }
                                }
                            }).show();
                }
            } else if (getString(R.string.pref_ga_display_features_key).equals(key)) {

                final SwitchPreference connectionPref = (SwitchPreference) findPreference(key);
                boolean isOn = connectionPref.isChecked();
                log.info("User changed the pref_ga_display_features_key Setting, current value is {}", isOn);

                ChameleonApplication.getMetrics().setShouldEnableAdvertisingIdCollection(isOn);
            }

            else {
                log.info("Unknown preference change for key '{}'", key);
            }

            log.info("Preferences changed for {}", key);
        }

        private String getVersionName() {

            String versionName = "";
            try {
                PackageInfo pinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                versionName =  pinfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                log.warn("Caught NameNotFoundException", e);
            }

            log.info("versionName is {}", versionName);
            return versionName;
        }



    }
}
