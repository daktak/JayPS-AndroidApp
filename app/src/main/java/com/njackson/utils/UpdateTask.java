package com.njackson.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.njackson.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateTask {

    private static final String TAG = "PB-UpdateTask";

    private static final String UPDATE_URL = "https://api.github.com/repos/daktak/JayPS-AndroidApp/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/daktak/JayPS-AndroidApp/releases/latest";
    private static final String CHANNEL_ID = "jayps-updates";
    private static final int NOTIFICATION_ID = 1000;

    private final Context context;
    private final boolean showToast;

    public UpdateTask(Context context, boolean showToast) {
        this.context = context.getApplicationContext();
        this.showToast = showToast;
        if (showToast) {
            Toast.makeText(context, context.getResources().getString(R.string.checking_new_version), Toast.LENGTH_SHORT).show();
        }
    }

    public void update() {
        new Thread(this::run).start();
    }

    private void run() {
        String json = fetch(UPDATE_URL);
        if (json == null) {
            if (showToast) {
                main(() -> Toast.makeText(context, R.string.update_error, Toast.LENGTH_LONG).show());
            }
            return;
        }
        main(() -> process(json));
    }

    private String fetch(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status != 200) {
                return null;
            }
            InputStream in = connection.getInputStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void process(String json) {
        try {
            JSONObject release = new JSONObject(json);

            String currentVersion = getPackageVersion();
            String latestVersion = release.getString("tag_name");
            boolean isPreRelease = release.getBoolean("prerelease");

            if (!isPreRelease && compareVersions(currentVersion, latestVersion) >= 0) {
                if (showToast) {
                    Toast.makeText(context, R.string.update_already_latest, Toast.LENGTH_SHORT).show();
                }
                return;
            }

            notifyUpdate(latestVersion);
        } catch (Exception e) {
            if (showToast) {
                Toast.makeText(context, R.string.update_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getPackageVersion() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void notifyUpdate(String latestVersion) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
                return;
            }
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_update_notification_content_title),
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.app_update_notification_content_title))
                .setContentText(context.getString(R.string.app_update_notification_content_text) + " " + latestVersion);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        if (v == null) {
            return new int[]{0};
        }
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = parsePart(parts[i]);
        }
        return out;
    }

    private static int parsePart(String part) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    private void main(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }
}
