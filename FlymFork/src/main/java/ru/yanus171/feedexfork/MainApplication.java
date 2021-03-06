/**
 * Flym
 * <p/>
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ru.yanus171.feedexfork;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import java.lang.reflect.Method;
import java.util.Locale;

import ru.yanus171.feedexfork.activity.BaseActivity;
import ru.yanus171.feedexfork.utils.Dog;
import ru.yanus171.feedexfork.utils.PrefUtils;
import ru.yanus171.feedexfork.utils.DebugApp;

import static ru.yanus171.feedexfork.service.FetcherService.Status;

public class MainApplication extends Application {

    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

    public static final String NOTIFICATION_CHANNEL_ID = "main_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();


        Status();

        BaseActivity.InitLocale( mContext );

        Thread.setDefaultUncaughtExceptionHandler(new DebugApp().new UncaughtExceptionHandler(this));

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                m.invoke(null);
            } catch (Exception e) {
                Dog.e("disableDeathOnFileUriExposure", e);
            }
        }
        PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false);


        if (Build.VERSION.SDK_INT >= 26) {
            Context context = MainApplication.getContext();
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.app_name));
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

    }


    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        BaseActivity.InitLocale( mContext );
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext( BaseActivity.InitLocale( base ));
    }
}
