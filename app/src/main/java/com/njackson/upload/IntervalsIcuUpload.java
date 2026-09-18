package com.njackson.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.njackson.R;
import com.njackson.application.PebbleBikeApplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

/**
 * intervals.icu upload using API key (Basic Auth).
 *
 * Flow:
 *   1) POST https://intervals.icu/api/v1/athlete/0/activities
 *      multipart: file=gpx/tcx
 *      Basic Auth: username="API_KEY", password=<api_key>
 *      Query params: description="KayPS activity"
 *   2) Check response for success (201 Created)
 */
public class IntervalsIcuUpload {

    private static final String TAG = "PB-IntervalsIcuUpload";
    private static final String UA = "Mozilla/5.0 (Linux; Android) KayPS";
    private static final String UPLOAD_URL = "https://intervals.icu/api/v1/athlete/0/activities";
    private static final int TIMEOUT_CONNECT_MS = 15000;
    private static final int TIMEOUT_READ_MS = 60000;

    @Inject SharedPreferences _sharedPreferences;

    private final Context _context;

    public IntervalsIcuUpload(Context context) {
        ((PebbleBikeApplication) context.getApplicationContext()).inject(this);
        _context = context.getApplicationContext();
    }

    public void upload(final String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            toast("intervals.icu: no API key set");
            return;
        }
        toast("intervals.icu: uploading... Please wait");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    Log.i(TAG, "upload start (apiKeyLen=" + apiKey.trim().length() + ")");
                    AdvancedLocation advancedLocation = new AdvancedLocation(_context);
                    String activityType = _sharedPreferences.getString("TCX_ACTIVITY_TYPE", "Biking");
                    String filename;
                    String data;
                    if (advancedLocation.hasPowerData()) {
                        data = advancedLocation.getTCX(activityType);
                        filename = "activity.tcx";
                    } else {
                        data = advancedLocation.getGPX(false);
                        filename = "activity.gpx";
                    }
                    message = _upload(apiKey.trim(), data, filename);
                } catch (Exception e) {
                    Log.e(TAG, "Exception:" + e, e);
                    message = "Error - " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
                final String result = message;
                Log.i(TAG, "RESULT: " + result);
                toast("intervals.icu: " + result);
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

    private String _upload(String apiKey, String data, String filename) throws Exception {
        // Build Basic Auth header: "API_KEY:" + apiKey
        String auth = "API_KEY:" + apiKey;
        String encodedAuth = Base64.encodeToString(auth.getBytes("UTF-8"), Base64.NO_WRAP);
        String authHeader = "Basic " + encodedAuth;

        // Add description query param
        String urlWithParams = UPLOAD_URL + "?description=KayPS%20activity";

        byte[] fileBytes = data.getBytes("UTF-8");
        String boundary = "===pb" + System.currentTimeMillis() + "===";
        byte[] body = buildMultipart(boundary, filename, fileBytes);

        URL url = new URL(urlWithParams);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
        conn.setReadTimeout(TIMEOUT_READ_MS);
        conn.setFixedLengthStreamingMode(body.length);

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.write(body);
        out.flush();
        out.close();

        int code = conn.getResponseCode();
        String resp = readStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();

        Log.d(TAG, "POST /activities -> code=" + code + " resp=" + (resp == null ? "<null>" : resp.length()));

        return parseUploadResponse(code, resp);
    }

    private String parseUploadResponse(int code, String body) {
        if (code != 201 && code != 200) {
            String errorMsg = "Error - upload failed (" + code + ")";
            if (body != null) {
                try {
                    JSONObject j = new JSONObject(body);
                    String error = j.optString("error", "");
                    String message = j.optString("message", "");
                    if (!error.isEmpty()) errorMsg += ": " + error;
                    if (!message.isEmpty()) errorMsg += " - " + message;
                } catch (JSONException ignored) {
                }
            }
            return errorMsg;
        }
        if (body == null) {
            return "Activity uploaded to intervals.icu";
        }
        try {
            JSONObject j = new JSONObject(body);
            String id = j.optString("id", "");
            if (!id.isEmpty()) {
                return _context.getString(R.string.strava_upload_success) + " (id: " + id + ")";
            }
            return "Activity uploaded to intervals.icu";
        } catch (JSONException e) {
            Log.e(TAG, "JSONException:" + e, e);
            return "Activity uploaded to intervals.icu";
        }
    }

    private byte[] buildMultipart(String boundary, String filename, byte[] fileBytes) throws java.io.IOException {
        String crlf = "\r\n";
        String hyphens = "--";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + crlf);
        out.writeBytes("Content-Type: application/octet-stream" + crlf + crlf);
        out.write(fileBytes);
        out.writeBytes(crlf + hyphens + boundary + hyphens + crlf);
        out.flush();
        out.close();
        return baos.toByteArray();
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
            Log.e(TAG, "readStream Exception:" + e, e);
            return null;
        }
    }
}