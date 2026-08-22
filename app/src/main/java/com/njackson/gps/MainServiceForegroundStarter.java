package com.njackson.gps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.preference.PreferenceManager;
import android.util.Log;

import com.njackson.R;
import com.njackson.activities.MainActivity;

/**
 * Created by njackson on 06/01/15.
 */
public class MainServiceForegroundStarter implements IForegroundServiceStarter {

    private String TAG = "PB-MainServiceForegroundStarter";

    private static final String CHANNEL_ID = "jayps_location_channel";
    private NotificationCompat.Builder builder = null;
    private final int myID = 1000;

    private void ensureChannel(Context context, int priority) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return;
        int importance = priority >= NotificationCompat.PRIORITY_HIGH
                ? NotificationManager.IMPORTANCE_HIGH
                : NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "KayPS Location", importance);
        channel.setShowBadge(false);
        mgr.createNotificationChannel(channel);
    }

    @Override
    public void startServiceForeground(Service service, String title, String contentText, int priority) {

        Intent i = new Intent(service, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendIntent = PendingIntent.getActivity(service, 0, i, PendingIntent.FLAG_IMMUTABLE);

        ensureChannel(service, priority);

        builder = new NotificationCompat.Builder(service, CHANNEL_ID);

        builder.setContentTitle(title).setContentText(contentText)
                .setSmallIcon(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP ? R.drawable.ic_notification : R.drawable.ic_launcher)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(priority)
                .setContentIntent(pendIntent);
        Notification notification = builder.build();

        // A "location" foreground service requires the location permission to be granted.
        // In indoor mode (and whenever the permission is not granted) we use the "dataSync"
        // type instead so the service can start without crashing.
        int fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        if (!isIndoor(service) && hasLocationPermission(service)) {
            fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(myID, notification, fgsType);
        } else {
            service.startForeground(myID, notification);
        }
    }

    private boolean isIndoor(Service service) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
        return prefs.getBoolean("INDOOR_MODE", false);
    }

    private boolean hasLocationPermission(Service service) {
        return ContextCompat.checkSelfPermission(service, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(service, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void stopServiceForeground(Service service) {
        service.stopForeground(true);
        builder = null;
    }

    public void changeNotification(Service context, String text, int priority) {
        if (builder != null) {
            builder.setContentText(text);
            builder.setPriority(priority);

            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(myID, builder.build());
        }
    }
}
