package com.njackson.upload;

import android.util.Log;

import com.njackson.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Reads the rider profile from intervals.icu so it can seed the app's own rider settings.
 *
 * <p>One call to {@code GET /api/v1/athlete/0} covers every value we import, because that endpoint
 * returns the athlete object plus its per-sport settings. Athlete id {@code 0} means "the athlete
 * this API key belongs to", so no athlete id has to be stored locally.
 *
 * <p>Every field is optional. intervals.icu omits values the rider has never set, so anything absent
 * stays {@code null} here and is left untouched on import rather than being written as 0.
 */
public class IntervalsIcuAthlete {

    private static final String TAG = "PB-IntervalsIcuAthlete";
    private static final String ATHLETE_URL = "https://intervals.icu/api/v1/athlete/0";
    private static final int TIMEOUT_CONNECT_MS = 15000;
    private static final int TIMEOUT_READ_MS = 30000;

    /** Rider values read from intervals.icu. Any field may be {@code null} when not set there. */
    public static final class Profile {
        public Integer weightKg;
        public Integer heightCm;
        public Integer ageYears;
        public String sex;
        public Integer restingHr;
        public Integer ftp;
        public Integer maxHr;
        /** Which sport the FTP and max HR came from, for display. */
        public String sport;
        /** Preference keys this profile can actually write. */
        public final List<String> keys = new ArrayList<>();
    }

    /** Parses the athlete endpoint response. Package visible so it can be tested off-device. */
    static Profile parse(String body, String requestedSport) throws JSONException {
        Profile profile = new Profile();
        JSONObject athlete = new JSONObject(body);

        Integer weight = optInt(athlete, "weight");
        if (weight == null) {
            weight = optInt(athlete, "icu_weight");
        }
        if (weight != null && weight > 0) {
            profile.weightKg = weight;
            profile.keys.add(Constants.PREF_RIDER_WEIGHT);
        }

        // Height is stored in metres by intervals.icu and in centimetres by the app.
        Double heightM = optDouble(athlete, "height");
        if (heightM != null && heightM > 0) {
            profile.heightCm = (int) Math.round(heightM * 100);
            profile.keys.add(Constants.PREF_RIDER_HEIGHT);
        }

        Integer age = ageFromDateOfBirth(optString(athlete, "icu_date_of_birth"));
        if (age != null) {
            profile.ageYears = age;
            profile.keys.add(Constants.PREF_RIDER_AGE);
        }

        String sex = sexOf(athlete);
        if (sex != null) {
            profile.sex = sex;
            profile.keys.add(Constants.PREF_RIDER_SEX);
        }

        Integer restingHr = optInt(athlete, "icu_resting_hr");
        if (restingHr != null && restingHr > 0) {
            profile.restingHr = restingHr;
            profile.keys.add(Constants.PREF_RIDER_RESTING_HR);
        }

        JSONObject sport = findSportSettings(athlete.optJSONArray("sportSettings"), requestedSport);
        if (sport != null) {
            Integer ftp = optInt(sport, "ftp");
            if (ftp != null && ftp > 0) {
                profile.ftp = ftp;
                profile.keys.add(Constants.PREF_FTP);
            }
            Integer maxHr = optInt(sport, "max_hr");
            if (maxHr != null && maxHr > 0) {
                profile.maxHr = maxHr;
                profile.keys.add(Constants.PREF_BLE_HRM_HRMAX);
            }
            profile.sport = String.join(", ", typesOf(sport));
        }
        return profile;
    }

    /**
     * Picks the sport settings for {@code requestedSport}, falling back to the first entry that
     * actually carries an FTP or max HR so a mismatched activity type still yields something usable.
     */
    static JSONObject findSportSettings(JSONArray settings, String requestedSport) {
        if (settings == null || settings.length() == 0) {
            return null;
        }
        String wanted = normaliseSport(requestedSport);
        JSONObject firstWithData = null;
        for (int i = 0; i < settings.length(); i++) {
            JSONObject entry = settings.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            boolean hasData = entry.optInt("ftp", 0) > 0 || entry.optInt("max_hr", 0) > 0;
            if (hasData && firstWithData == null) {
                firstWithData = entry;
            }
            if (typesOf(entry).contains(wanted)) {
                return entry;
            }
        }
        return firstWithData;
    }

    private static List<String> typesOf(JSONObject settings) {
        List<String> types = new ArrayList<>();
        JSONArray arr = settings.optJSONArray("types");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                types.add(normaliseSport(arr.optString(i, "")));
            }
        }
        // A single-sport entry is sometimes returned as a bare string instead of an array.
        String single = settings.optString("types", "");
        if (!single.isEmpty() && types.isEmpty()) {
            types.add(normaliseSport(single));
        }
        return types;
    }

    /** The app's TCX_ACTIVITY_TYPE values are "Biking"/"Running"/"Other"; intervals uses "Ride". */
    static String normaliseSport(String value) {
        if (value == null) {
            return "";
        }
        switch (value.trim().toLowerCase(Locale.US)) {
            case "biking":
            case "bike":
            case "ride":
            case "gravelride":
            case "mountainbikeride":
            case "ebikeride":
            case "emountainbikeride":
            case "virtualride":
                return "ride";
            case "running":
            case "run":
            case "trailrun":
            case "virtualrun":
            case "treadmillrunning":
                return "run";
            case "swimming":
            case "swim":
            case "openwaterswim":
            case "virtualswim":
                return "swim";
            default:
                return value.trim().toLowerCase(Locale.US);
        }
    }

    /** intervals.icu stores "M"/"F"; the app stores "m"/"f". Anything else is left alone. */
    static String sexOf(JSONObject athlete) {
        String raw = optString(athlete, "sex");
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.US);
        if (v.startsWith("f")) {
            return Constants.RIDER_SEX_FEMALE;
        }
        if (v.startsWith("m")) {
            return Constants.RIDER_SEX_MALE;
        }
        return null;
    }

    /**
     * Derives whole years from a date of birth. Returns {@code null} when the value is absent or not
     * a date we recognise, so a formatting change upstream cannot silently write age 0.
     */
    static Integer ageFromDateOfBirth(String dob) {
        if (dob == null) {
            return null;
        }
        String v = dob.trim();
        if (v.isEmpty()) {
            return null;
        }
        int year;
        int month;
        int day;
        try {
            String[] parts = v.split("[-/]");
            if (parts.length < 3) {
                return null;
            }
            year = Integer.parseInt(parts[0]);
            month = Integer.parseInt(parts[1]);
            day = Integer.parseInt(parts[2].length() > 2 ? parts[2].substring(0, 2) : parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - year;
        // Not yet had their birthday this year.
        if (today.get(Calendar.MONTH) + 1 < month
                || (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)) {
            age--;
        }
        if (age < 10 || age > 120) {
            return null;
        }
        return age;
    }

    /**
     * Reads an integer, going via double so a JSON {@code 75.5} rounds rather than truncating to 75.
     * {@code getInt} casts, which would silently drop the fraction on weight.
     */
    private static Integer optInt(JSONObject o, String key) {
        Double d = optDouble(o, key);
        return d == null ? null : (int) Math.round(d);
    }

    private static Double optDouble(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        try {
            return o.getDouble(key);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String optString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        String v = o.optString(key, "").trim();
        return v.isEmpty() ? null : v;
    }

    /**
     * Fetches and parses the profile. Blocking: callers must run it off the main thread.
     *
     * @throws Exception on network failure, auth rejection, or an unparseable response
     */
    public Profile fetchProfile(String apiKey, String requestedSport) throws Exception {
        String body = get(ATHLETE_URL, apiKey.trim());
        return parse(body, requestedSport);
    }

    private String get(String url, String apiKey) throws Exception {
        URL target = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod("GET");
        // intervals.icu personal keys use HTTP Basic with a literal "API_KEY" username.
        conn.setRequestProperty("Authorization", basicAuth(apiKey));
        conn.setRequestProperty("User-Agent", IntervalsIcuUpload.UA);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
        conn.setReadTimeout(TIMEOUT_READ_MS);

        int code = conn.getResponseCode();
        String body;
        try {
            body = readStream(code >= 200 && code < 300 ? conn.getInputStream()
                    : conn.getErrorStream());
        } finally {
            conn.disconnect();
        }
        Log.d(TAG, "GET athlete/0 -> code=" + code);
        if (code == 401 || code == 403) {
            throw new IllegalStateException("intervals.icu rejected the API key (HTTP " + code + ")");
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("intervals.icu returned HTTP " + code);
        }
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("intervals.icu returned an empty response");
        }
        return body;
    }

    private static String basicAuth(String apiKey) {
        String encoded = android.util.Base64.encodeToString(
                ("API_KEY:" + apiKey).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
        return "Basic " + encoded;
    }

    private static String readStream(InputStream is) {
        if (is == null) {
            return null;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "readStream Exception:" + e, e);
            return null;
        }
    }
}