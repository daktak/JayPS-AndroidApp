package com.njackson.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.njackson.application.PebbleBikeApplication;
import com.njackson.pebble.IMessageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

public class StravaUpload {

    private static final String TAG = "PB-StravaUpload";
    private static final String SELECT_URL = "https://www.strava.com/upload/select";
    private static final String UPLOAD_URL = "https://www.strava.com/upload/files";

    @Inject IMessageManager _messageManager;
    @Inject SharedPreferences _sharedPreferences;

    private final Context _context;

    public StravaUpload(Context context) {
        ((PebbleBikeApplication) context.getApplicationContext()).inject(this);
        _context = context.getApplicationContext();
    }

    public void upload(final String session) {
        if (session == null || session.trim().isEmpty()) {
            toast("Strava: no session cookie set");
            return;
        }
        toast("Strava: uploading... Please wait");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    AdvancedLocation advancedLocation = new AdvancedLocation(_context);
                    String gpx = advancedLocation.getGPX(false);
                    message = _upload(session.trim(), gpx);
                } catch (Exception e) {
                    Log.e(TAG, "Exception:" + e);
                    message = "Error - " + e.getMessage();
                }
                final String result = message;
                toast("Strava: " + result);
                if (_sharedPreferences.getBoolean("STRAVA_NOTIFICATION", false)) {
                    try {
                        _messageManager.sendMessageToPebble("JayPS - Strava", result);
                    } catch (Exception e) {
                        Log.e(TAG, "sendMessageToPebble Exception:" + e);
                    }
                }
            }
        }).start();
    }

    private void toast(final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_context, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String _upload(String session, String gpx) throws Exception {
        String cookie = "_strava4_session=" + session;

        String selectHtml = httpGet(SELECT_URL, cookie);
        if (selectHtml == null || !selectHtml.contains("Upload and Sync Your Activities")) {
            return "Error - invalid session cookie";
        }
        String token = scrapeCsrf(selectHtml);
        if (token == null) {
            return "Error - cannot read upload form";
        }

        String boundary = "===pb" + System.currentTimeMillis() + "===";
        byte[] gpxBytes = gpx.getBytes("UTF-8");
        String crlf = "\r\n";
        String hyphens = "--";

        URL url = new URL(UPLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) JayPS");
        conn.setRequestProperty("Cookie", cookie);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"_method\"" + crlf + crlf);
        out.writeBytes("post" + crlf);

        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"authenticity_token\"" + crlf + crlf);
        out.writeBytes(token + crlf);

        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"activity.gpx\"" + crlf);
        out.writeBytes("Content-Type: text/xml" + crlf + crlf);
        out.write(gpxBytes);
        out.writeBytes(crlf + hyphens + boundary + hyphens + crlf);
        out.flush();
        out.close();

        int code = conn.getResponseCode();
        String body = readStream(code == 200 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();

        if (code != 200 || body == null || !body.contains("workflow")) {
            return "Error - upload failed (" + code + ")";
        }
        try {
            String workflow = null;
            String error = "";
            if (body.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) {
                    JSONObject first = arr.getJSONObject(0);
                    workflow = first.optString("workflow");
                    error = first.optString("error");
                }
            } else {
                JSONObject j = new JSONObject(body);
                workflow = j.optString("workflow");
                error = j.optString("error");
            }
            if ("success".equals(workflow)) {
                return "Your activity has been created";
            }
            return "Error - " + (error.isEmpty() ? body : error);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException:" + e);
            return "Error - bad response";
        }
    }

    private String scrapeCsrf(String html) {
        Matcher m = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]+)\"").matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("name=\"authenticity_token\" value=\"([^\"]+)\"").matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    private String httpGet(String urlStr, String cookie) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) JayPS");
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestMethod("GET");
            conn.connect();
            int code = conn.getResponseCode();
            String body = readStream(code == 200 ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();
            return body;
        } catch (Exception e) {
            Log.e(TAG, "httpGet Exception:" + e);
            return null;
        }
    }

    private String readStream(InputStream is) {
        if (is == null) return null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
