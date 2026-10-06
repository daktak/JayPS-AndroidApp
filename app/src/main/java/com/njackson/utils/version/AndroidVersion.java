package com.njackson.utils.version;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.pm.PackageInfoCompat;

/**
 * Created by jay on 28/12/14.
 */
public class AndroidVersion implements IAndroidVersion{
    private final String TAG = "PB-Version";

    public String getVersionCode(Context context) {
        if (context == null) {
            return "";
        }

        long versionCode;

        // Get current version code and version name
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);

            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            versionCode = 0;
        }
        Log.d(TAG, "versionCode:" + versionCode);
        return String.format("%d", versionCode);
    }
}
