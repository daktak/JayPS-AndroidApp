package com.njackson.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.njackson.R;
import com.njackson.application.PebbleBikeApplication;
import com.njackson.pebble.IMessageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

/**
 * Strava upload using a session cookie (no OAuth).
 *
 * Flow (mirrors ~/git/StravaUploaderPy):
 *   1) GET https://www.strava.com/upload/select  with Cookie _strava4_session=
 *      -> validates the session and scrapes the csrf-token.
 *   2) POST https://www.strava.com/upload/files
 *      multipart: _method=post, authenticity_token=csrf, files[]=gpx
 *      -> checks "workflow" == "success"
 *
 * Does NOT use the /activities/{id} or /activities/{id}/edit endpoints.
 */
public class StravaUpload {

    private static final String TAG = "PB-StravaUpload";
    private static final String UA = "Mozilla/5.0 (Linux; Android) KayPS";
    private static final String SELECT_URL = "https://www.strava.com/upload/select";
    private static final String UPLOAD_URL = "https://www.strava.com/upload/files";
    private static final int TIMEOUT_CONNECT_MS = 15000;
    private static final int TIMEOUT_READ_MS = 60000;

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
                    Log.i(TAG, "upload start (sessionLen=" + session.trim().length() + ")");
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
                    message = _upload(session.trim(), data, filename);
                } catch (Exception e) {
                    Log.e(TAG, "Exception:" + e, e);
                    message = "Error - " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
                final String result = message;
                Log.i(TAG, "RESULT: " + result);
                toast("Strava: " + result);
                if (_sharedPreferences.getBoolean("STRAVA_NOTIFICATION", false)) {
                    try {
                        _messageManager.sendMessageToPebble("KayPS - Strava", result);
                    } catch (Exception e) {
                        Log.e(TAG, "sendMessageToPebble Exception:" + e, e);
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

    private String _upload(String session, String data, String filename) throws Exception {
        String cookie = "_strava4_session=" + session;

        // 1) GET the upload page: validate session + fetch csrf token
        String selectHtml = httpGet(SELECT_URL, cookie);
        Log.i(TAG, "GET select -> len=" + (selectHtml == null ? "null" : selectHtml.length()));
        if (selectHtml == null) {
            return "Error - cannot reach Strava (/upload/select failed)";
        }
        if (!selectHtml.contains("Upload and Sync Your Activities")) {
            return "Error - invalid session cookie";
        }
        String token = scrapeCsrf(selectHtml);
        if (token == null) {
            return "Error - cannot read Strava upload form";
        }

        // 2) POST the GPX/TCX file as a fixed-length multipart body (no chunked encoding)
        byte[] fileBytes = data.getBytes("UTF-8");
        String boundary = "===pb" + System.currentTimeMillis() + "===";
        byte[] body = buildMultipart(boundary, token, filename, fileBytes);

        URL url = new URL(UPLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Cookie", cookie);
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
        String resp = readStream(code == 200 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();

        Log.d(TAG, "POST /upload/files -> code=" + code + " resp=" + (resp == null ? "<null>" : resp.length()));

        if (code != 200 || resp == null) {
            return "Error - upload failed (" + code + ")";
        }
        return parseUploadResponse(code, resp);
    }

    private String parseUploadResponse(int code, String body) {
        if (code != 200 || body == null) {
            return "Error - upload failed (" + code + ")";
        }
        // StravaUploaderPy treats the mere presence of "workflow" in the response
        // as a successful (accepted) upload. Avoid optString("error"), which would
        // turn Strava's JSON "error":null into the literal string "null".
        if (!body.contains("workflow")) {
            return "Error - upload not confirmed by Strava";
        }
        try {
            JSONObject j = null;
            if (body.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) j = arr.getJSONObject(0);
            } else {
                j = new JSONObject(body);
            }
            String workflow = (j == null) ? null : optStr(j, "workflow");
            if ("success".equals(workflow)) {
                return _context.getString(R.string.strava_upload_success);
            }
            // workflow present (Strava accepted) but not yet "success"
            return "Activity uploaded to Strava" + (workflow != null ? " (status=" + workflow + ")" : "");
        } catch (JSONException e) {
            Log.e(TAG, "JSONException:" + e, e);
            return "Activity uploaded to Strava";
        }
    }

    private static String optStr(JSONObject j, String key) {
        Object o = j.opt(key);
        if (o == null || JSONObject.NULL.equals(o)) return null;
        return o.toString();
    }

    private byte[] buildMultipart(String boundary, String token, String filename, byte[] fileBytes) throws java.io.IOException {
        String crlf = "\r\n";
        String hyphens = "--";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"_method\"" + crlf + crlf);
        out.writeBytes("post" + crlf);
        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"authenticity_token\"" + crlf + crlf);
        out.writeBytes(token + crlf);
        out.writeBytes(hyphens + boundary + crlf);
        out.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"" + filename + "\"" + crlf);
        out.writeBytes("Content-Type: text/xml" + crlf + crlf);
        out.write(fileBytes);
        out.writeBytes(crlf + hyphens + boundary + hyphens + crlf);
        out.flush();
        out.close();
        return baos.toByteArray();
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
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
            conn.setReadTimeout(TIMEOUT_READ_MS);
            conn.connect();
            int code = conn.getResponseCode();
            String body = readStream(code == 200 ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();
            return body;
        } catch (Exception e) {
            Log.e(TAG, "httpGet Exception:" + e, e);
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
            Log.e(TAG, "readStream Exception:" + e, e);
            return null;
        }
    }
}
