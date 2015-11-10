package com.trioscope.chameleon.metrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.trioscope.chameleon.R;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 7/15/15.
 */
@Slf4j
public class MetricsHelper {
    private static final int DISPATCH_PERIOD_IN_SECONDS = 1800;
    private static final String GOOGLE_ANALYTICS_CHAMELEON_TRACKING_ID = "UA-65062909-1";

    @NonNull
    private Tracker metricsTracker;

    public MetricsHelper(Context context){
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(context);
        analytics.setLocalDispatchPeriod(DISPATCH_PERIOD_IN_SECONDS);

        metricsTracker = analytics.newTracker(GOOGLE_ANALYTICS_CHAMELEON_TRACKING_ID);

        // Provide unhandled exceptions reports. Do that first after creating the tracker
        metricsTracker.enableExceptionReporting(true);

        // Enable automatic activity tracking for your app
        metricsTracker.enableAutoActivityTracking(true);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean shouldEnableAdvertisingIdCollection = preferences.getBoolean(context.getString(R.string.pref_ga_display_features_key), true);

        setShouldEnableAdvertisingIdCollection(shouldEnableAdvertisingIdCollection);
    }

    public void setShouldEnableAdvertisingIdCollection(boolean value) {

        // Enable Remarketing, Demographics & Interests reports
        // https://developers.google.com/analytics/devguides/collection/android/display-features
        metricsTracker.enableAdvertisingIdCollection(value);
        log.info("setting enableAdvertisingIdCollection to {}", value);
    }

    public void sendTime(String category, String label, long timeInMillis) {
        metricsTracker.send(new HitBuilders.TimingBuilder()
                        .setCategory(category)
                        .setLabel(label)
                        .setValue(timeInMillis)
                        .setVariable(label)
                        .build()
        );
    }

    public void sendMergeTimeToVideoDuration(float value) {
        metricsTracker.send(new HitBuilders.ScreenViewBuilder()
                .setCustomMetric(1, value)
                .build());
    }

    public void sendConnectionHeartBeatTimeoutMetric(int value) {
        metricsTracker.send(new HitBuilders.ScreenViewBuilder()
                .setCustomMetric(2, value)
                .build());
    }
}
