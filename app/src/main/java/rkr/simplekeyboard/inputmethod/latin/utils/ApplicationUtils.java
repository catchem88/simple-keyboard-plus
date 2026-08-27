/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2017 Raimondas Rimkus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public final class ApplicationUtils {
    private static final String TAG = ApplicationUtils.class.getSimpleName();

    private ApplicationUtils() {
        // This utility class is not publicly instantiable.
    }

    /*
        Name of the manifest activity-alias that carries the launcher entry. Disabling it removes the
        app from the launcher without affecting SettingsActivity, which the system still launches.
        Declared with a leading dot in the manifest, so it resolves against the namespace rather than
        the application id.
    */
    private static final String LAUNCHER_ALIAS_CLASS_NAME =
            "rkr.simplekeyboard.inputmethod.latin.settings.SettingsLauncherAlias";

    /*
        Show or hide the launcher icon, writing only when the state actually differs.

        The enabled state belongs to the package manager, not to the app, so it survives restarts and
        updates and resets on uninstall. Preferences are backed up but that state is not, so a restored
        install can have the preference saying hidden while the icon is visible. Making this idempotent
        lets it be called to reconcile the two without writing on every settings visit.

        DONT_KILL_APP matters: without it the system kills this process, taking the running keyboard
        down with it.
    */
    public static void setLauncherIconVisible(final Context context, final boolean visible) {
        final ComponentName alias =
                new ComponentName(context.getPackageName(),LAUNCHER_ALIAS_CLASS_NAME);
        final PackageManager packageManager = context.getPackageManager();
        try {
            //DEFAULT means the manifest value applies, and the alias ships enabled
            final int current = packageManager.getComponentEnabledSetting(alias);
            final boolean currentlyVisible =
                    current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            if(currentlyVisible == visible) {
                return;
            }

            final int state;
            if(visible) {
                state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            }
            else {
                state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            }
            packageManager.setComponentEnabledSetting(alias,state,PackageManager.DONT_KILL_APP);
        } catch (final IllegalArgumentException e) {
            //The alias is missing, which can only happen if the manifest and this constant disagree
            Log.e(TAG, "Could not change launcher icon visibility.", e);
        }
    }

    public static int getActivityTitleResId(final Context context,
            final Class<? extends Activity> cls) {
        final ComponentName cn = new ComponentName(context, cls);
        try {
            final ActivityInfo ai = context.getPackageManager().getActivityInfo(cn, 0);
            if (ai != null) {
                return ai.labelRes;
            }
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Failed to get settings activity title res id.", e);
        }
        return 0;
    }

    /**
     * A utility method to get the application's PackageInfo.versionName
     * @return the application's PackageInfo.versionName
     */
    public static String getVersionName(final Context context) {
        try {
            if (context == null) {
                return "";
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return "";
    }

    /**
     * A utility method to get the application's PackageInfo.versionCode
     * @return the application's PackageInfo.versionCode
     */
    public static int getVersionCode(final Context context) {
        try {
            if (context == null) {
                return 0;
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionCode;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return 0;
    }
}
