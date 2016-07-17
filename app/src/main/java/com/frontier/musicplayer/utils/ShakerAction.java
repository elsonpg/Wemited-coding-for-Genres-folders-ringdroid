package com.frontier.musicplayer.utils;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.view.Display;

import com.frontier.musicplayer.MusicService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.frontier.musicplayer.utils.Action.*;


/**
 * Created by Elson on 6/24/2016.
 */
public class ShakerAction extends MusicService implements OnSharedPreferenceChangeListener,SensorEventListener {

    /**
     * Cached app-wide SharedPreferences instance.
     */
    private static SharedPreferences sSettings;
    /**
     * Magnitude of last sensed acceleration.
     */
    private double mAccelLast;
    /**
     * Filtered acceleration used for shake detection.
     */
    private double mAccelFiltered;
    /**
     * Elapsed realtime of last shake action.
     */
    private long mLastShakeTime;
    /**
     * Minimum time in milliseconds between shake actions.
     */
    private static final int MIN_SHAKE_PERIOD = 500;
    /**
     * Minimum jerk required for shake.
     */

    private double mShakeThreshold;
    /**
     * What to do when an accelerometer shake is detected.
     */
    private Action mShakeAction;

    /**
     * The SensorManager service.
     */
    private SensorManager mSensorManager;

    public void onCreate() {

        SharedPreferences settings = getSettings(this);
        settings.registerOnSharedPreferenceChangeListener(this);

        mShakeAction = settings.getBoolean(PrefKeys.ENABLE_SHAKE, PrefDefaults.ENABLE_SHAKE) ? Action.getAction(settings, PrefKeys.SHAKE_ACTION, PrefDefaults.SHAKE_ACTION) : Action.Nothing;
        mShakeThreshold = settings.getInt(PrefKeys.SHAKE_THRESHOLD, PrefDefaults.SHAKE_THRESHOLD) / 10.0f;
    }


    public static SharedPreferences getSettings(ShakerAction context) {
        if (sSettings == null) {
            sSettings = PreferenceManager.getDefaultSharedPreferences(context);
            }
        return sSettings;
    }

    /**
     * Setup the accelerometer.
     */
    private void setupSensor() {
        if (mShakeAction == Action.Nothing) {
            if (mSensorManager != null)
                mSensorManager.unregisterListener(this);
        } else {
            if (mSensorManager == null)
                mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void loadPreference(String key) {


        SharedPreferences settings = getSettings(this);

        if (PrefKeys.ENABLE_SHAKE.equals(key) || PrefKeys.SHAKE_ACTION.equals(key)) {
            mShakeAction = settings.getBoolean(PrefKeys.ENABLE_SHAKE, PrefDefaults.ENABLE_SHAKE) ? Action.getAction(settings, PrefKeys.SHAKE_ACTION, PrefDefaults.SHAKE_ACTION) : Action.Nothing;
            setupSensor();
        } else if (PrefKeys.SHAKE_THRESHOLD.equals(key)) {
            mShakeThreshold = settings.getInt(PrefKeys.SHAKE_THRESHOLD, PrefDefaults.SHAKE_THRESHOLD) / 10.0f;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent se)
    {
        double x = se.values[0];
        double y = se.values[1];
        double z = se.values[2];

        double accel = Math.sqrt(x*x + y*y + z*z);
        double delta = accel - mAccelLast;
        mAccelLast = accel;

        double filtered = mAccelFiltered * 0.9f + delta;
        mAccelFiltered = filtered;

        if (filtered > mShakeThreshold) {
            long now = SystemClock.elapsedRealtime();
            if (now - mLastShakeTime > MIN_SHAKE_PERIOD) {
                mLastShakeTime = now;
                performAction(mShakeAction, null);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
    }

    /**
     * Execute the given action.
     *
     * @param action The action to execute.
     * @param receiver Optional. If non-null, update the PlaybackActivity with
     * new song or state from the executed action. The activity will still be
     * updated by the broadcast if not passed here; passing it just makes the
     * update immediate.
     */
    public void performAction(Action action, MusicService receiver) {

        Intent intent = new Intent(this, MusicService.class);
        switch (action) {

            case Nothing:
                break;
            case PlayPause:
                if(isPlaying())
                intent.setAction(MusicService.PAUSE_ACTION);
                else intent.setAction(MusicService.CMDPLAY);
                break;
            case NextSong:
                intent.setAction(MusicService.NEXT_ACTION);
                break;
            case PreviousSong:
                intent.setAction(MusicService.PREVIOUS_ACTION);

        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences settings, String key)
    {
        loadPreference(key);
    }


}