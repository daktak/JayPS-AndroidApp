package com.njackson.utils;

import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;
import com.njackson.R;
import com.github.zafarkhaja.semver.Version;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class UpdateTask extends AsyncTask<String, String, String> {
    private static final String TAG = "PB-UpdateTask";

    private Context context;
    private boolean toShowToast;
   // public static final String updateUrl = "https://api.github.com/repos/team-mount-ventoux/JayPS-AndroidApp/releases/latest";
   // public static final String releaseURL = "https://api.github.com/team-mount-ventoux/JayPS-AndroidApp/releases/latest";
    public static final String updateUrl = "https://api.github.com/repos/daktak/JayPS-AndroidApp/releases/latest";
    public static final String releaseURL = "https://api.github.com/daktak/JayPS-AndroidApp/releases/latest";

    public UpdateTask(Context context, boolean showToast) {
        this.context = context;
        this.toShowToast = showToast;
        if (this.toShowToast) Toast.makeText(context, context.getResources().getString(R.string.checking_new_version), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected String doInBackground(String... uri) {
        HttpClient httpclient = new DefaultHttpClient();
        HttpResponse response;
        String responseString = null;
        try {
            response = httpclient.execute(new HttpGet(uri[0]));
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() == 200) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                responseString = out.toString();
                out.close();
            } else {
                // Close the connection.
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
            return null;
        }
        return responseString;
    }
    
    private Version getVersion(String ver) {
        Log.d(TAG, ver);
        String version = ver.startsWith("v") ? ver.substring(1) : ver;
        String[] parts = version.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<parts.length;i++) {
            sb.append(".");
            sb.append(Integer.parseInt(parts[i]));
        }
        String out = sb.toString().substring(1);
        Log.d(TAG, out);
        Version vers = Version.valueOf(out);
        return vers;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        try {
            JSONObject release = new JSONObject(result);

            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            Version currentVer = getVersion(pInfo.versionName);

            String latestVersion = release.getString("tag_name");
            Version latVersion = getVersion(latestVersion);
            boolean isPreRelease = release.getBoolean("prerelease");

            if (!isPreRelease && currentVer.greaterThanOrEqualTo(latVersion)) {
                // Your version is ahead of or same as the latest.
                if (toShowToast)
                    Toast.makeText(context, R.string.update_already_latest, Toast.LENGTH_SHORT).show();
            } else {
                String downloadUrl = release.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");

                final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                final PendingIntent pendingIntent
                        = PendingIntent.getActivity(context, 0, intent, 0);

                //final String channelId = "JayPS-Channel";
                final NotificationCompat.Builder notificationBuilder
                        = new NotificationCompat.Builder(context)//, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setContentTitle(context
                                .getString(R.string.app_update_notification_content_title))
                        .setContentText(context
                                .getString(R.string.app_update_notification_content_text)
                                + " " + latestVersion);

                final NotificationManagerCompat notificationManager
                        = NotificationManagerCompat.from(context);
                notificationManager.notify(1000, notificationBuilder.build());

            }
        } catch (Exception e) {
            e.printStackTrace();
            if (this.toShowToast)
                Toast.makeText(context, R.string.update_error, Toast.LENGTH_LONG).show();
        }
    }

    public void update() {
        super.execute(updateUrl);
    }
}
