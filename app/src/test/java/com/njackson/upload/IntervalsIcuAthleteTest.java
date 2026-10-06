package com.njackson.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.njackson.Constants;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Covers the conversions that would silently write wrong rider values if they broke: intervals.icu
 * reports height in metres, age only as a date of birth, and omits anything the rider never set.
 *
 * <p>These assertions run against JSON shaped like the real {@code GET /api/v1/athlete/0} response,
 * built from the published OpenAPI schema for {@code WithSportSettings}.
 */
public class IntervalsIcuAthleteTest {

    @Test
    public void parsesEveryRiderFieldFromAFullProfile() throws Exception {
        String body = "{"
                + "\"weight\": 75.5,"
                + "\"height\": 1.80,"
                + "\"icu_date_of_birth\": \"1990-04-15\","
                + "\"sex\": \"M\","
                + "\"icu_resting_hr\": 48,"
                + "\"sportSettings\": ["
                + "  {\"id\": 1, \"types\": [\"Run\"], \"ftp\": 310, \"max_hr\": 190},"
                + "  {\"id\": 2, \"types\": [\"Ride\"], \"ftp\": 265, \"max_hr\": 185}"
                + "]}";

        IntervalsIcuAthlete.Profile p = IntervalsIcuAthlete.parse(body, "Biking");

        assertEquals(Integer.valueOf(76), p.weightKg);
        // 1.80 m must land as 180 cm, not 1 or 2.
        assertEquals(Integer.valueOf(180), p.heightCm);
        assertEquals(Constants.RIDER_SEX_MALE, p.sex);
        assertEquals(Integer.valueOf(48), p.restingHr);
        // "Biking" has to resolve to the Ride sport settings, not the first entry (Run).
        assertEquals(Integer.valueOf(265), p.ftp);
        assertEquals(Integer.valueOf(185), p.maxHr);
        assertTrue(p.ageYears != null && p.ageYears > 10);
        assertEquals(7, p.keys.size());
    }

    @Test
    public void heightConvertsFromMetresToCentimetres() throws Exception {
        IntervalsIcuAthlete.Profile p =
                IntervalsIcuAthlete.parse("{\"height\": 1.73}", "Biking");
        assertEquals(Integer.valueOf(173), p.heightCm);
    }

    @Test
    public void roundWeightIsAcceptedAsAFloatValue() throws Exception {
        IntervalsIcuAthlete.Profile p =
                IntervalsIcuAthlete.parse("{\"weight\": 72.0}", "Biking");
        assertEquals(Integer.valueOf(72), p.weightKg);
    }

    @Test
    public void femaleSexIsRecognisedAndOtherValuesAreNotGuessed() throws Exception {
        assertEquals(Constants.RIDER_SEX_FEMALE,
                IntervalsIcuAthlete.sexOf(new JSONObject("{\"sex\":\"F\"}")));
        assertEquals(Constants.RIDER_SEX_MALE,
                IntervalsIcuAthlete.sexOf(new JSONObject("{\"sex\":\"Male\"}")));
        // An unrecognised value must not be forced onto one of our two options.
        assertNull(IntervalsIcuAthlete.sexOf(new JSONObject("{\"sex\":\"\"}")));
        assertNull(IntervalsIcuAthlete.sexOf(new JSONObject("{}")));
    }

    @Test
    public void missingFieldsStayNullRatherThanBecomingZero() throws Exception {
        IntervalsIcuAthlete.Profile p = IntervalsIcuAthlete.parse(
                "{\"weight\": 75, \"sportSettings\": []}", "Biking");

        assertEquals(Integer.valueOf(75), p.weightKg);
        assertNull(p.heightCm);
        assertNull(p.ageYears);
        assertNull(p.sex);
        assertNull(p.restingHr);
        assertNull(p.ftp);
        assertNull(p.maxHr);
        assertEquals(1, p.keys.size());
    }

    @Test
    public void explicitNullsAreTreatedAsAbsent() throws Exception {
        IntervalsIcuAthlete.Profile p = IntervalsIcuAthlete.parse(
                "{\"weight\": null, \"height\": null, \"icu_resting_hr\": null,"
                        + "\"icu_date_of_birth\": null, \"sex\": null}", "Biking");

        assertTrue(p.keys.isEmpty());
        assertNull(p.weightKg);
        assertNull(p.heightCm);
        assertNull(p.restingHr);
    }

    @Test
    public void ageIsDerivedFromDateOfBirth() {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        assertEquals(Integer.valueOf(30), IntervalsIcuAthlete.ageFromDateOfBirth((year - 30) + "-06-15"));
        // Birthday not reached yet this year: one year younger.
        assertEquals(Integer.valueOf(29), IntervalsIcuAthlete.ageFromDateOfBirth((year - 30) + "-12-31"));
        assertEquals(Integer.valueOf(35), IntervalsIcuAthlete.ageFromDateOfBirth((year - 35) + "-01-01"));
    }

    @Test
    public void unparseableDateOfBirthYieldsNoAge() {
        // Age 0 would be written straight into preferences and read as "unset" later.
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth(null));
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth(""));
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth("   "));
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth("not-a-date"));
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth("1990"));
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth("1899-01-01"));
        assertNull(IntervalsIcuAthlete.ageFromDateOfBirth("1990-13-01"));
    }

    @Test
    public void slashSeparatedDatesAreAccepted() {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        assertEquals(Integer.valueOf(40), IntervalsIcuAthlete.ageFromDateOfBirth((year - 40) + "/03/02"));
    }

    @Test
    public void activityTypeMapsOntoTheMatchingSport() {
        assertEquals("ride", IntervalsIcuAthlete.normaliseSport("Biking"));
        assertEquals("ride", IntervalsIcuAthlete.normaliseSport("Ride"));
        assertEquals("run", IntervalsIcuAthlete.normaliseSport("Running"));
        assertEquals("run", IntervalsIcuAthlete.normaliseSport("Run"));
        assertEquals("swim", IntervalsIcuAthlete.normaliseSport("Swimming"));
    }

    @Test
    public void unknownActivityTypeFallsBackToASportThatHasData() throws Exception {
        // "Other" maps to nothing in intervals.icu, but we still want FTP rather than nothing.
        String body = "{\"sportSettings\": ["
                + "{\"id\": 1, \"types\": [\"Swim\"], \"ftp\": 95, \"max_hr\": 180},"
                + "{\"id\": 2, \"types\": [\"Ride\"], \"ftp\": 250, \"max_hr\": 186}]}";

        IntervalsIcuAthlete.Profile p = IntervalsIcuAthlete.parse(body, "Other");
        assertEquals(Integer.valueOf(95), p.ftp);
        assertEquals(Integer.valueOf(180), p.maxHr);
    }

    @Test
    public void requestedSportWithNoSettingsFallsBackToTheFirstWithData() throws Exception {
        String body = "{\"sportSettings\": ["
                + "{\"id\": 1, \"types\": [\"Swim\"], \"ftp\": 95},"
                + "{\"id\": 2, \"types\": [\"Ride\"], \"ftp\": 250}]}";

        assertEquals(Integer.valueOf(95),
                IntervalsIcuAthlete.parse(body, "Running").ftp);
    }

    @Test
    public void sportSettingsWithoutFtpOrMaxHrAreSkipped() throws Exception {
        String body = "{\"sportSettings\": [{\"id\": 1, \"types\": [\"Swim\"]},"
                + "{\"id\": 2, \"types\": [\"Ride\"], \"ftp\": 250, \"max_hr\": 186}]}";

        IntervalsIcuAthlete.Profile p = IntervalsIcuAthlete.parse(body, "Biking");
        assertEquals(Integer.valueOf(250), p.ftp);
        assertEquals(Integer.valueOf(186), p.maxHr);
    }

    @Test
    public void icuWeightIsUsedWhenWeightIsAbsent() throws Exception {
        IntervalsIcuAthlete.Profile p =
                IntervalsIcuAthlete.parse("{\"icu_weight\": 69.4}", "Biking");
        assertEquals(Integer.valueOf(69), p.weightKg);
    }

    @Test
    public void zeroValuesAreNotImportedAsRealMeasurements() throws Exception {
        // A placeholder 0 from upstream would otherwise overwrite a good local value.
        IntervalsIcuAthlete.Profile p = IntervalsIcuAthlete.parse(
                "{\"weight\": 0, \"height\": 0, \"icu_resting_hr\": 0,"
                        + "\"sportSettings\": [{\"types\": [\"Ride\"], \"ftp\": 0, \"max_hr\": 0}]}",
                "Biking");

        assertNull(p.weightKg);
        assertNull(p.heightCm);
        assertNull(p.restingHr);
        assertNull(p.ftp);
        assertNull(p.maxHr);
        assertTrue(p.keys.isEmpty());
    }

    @Test
    public void negativeValuesAreRejected() throws Exception {
        IntervalsIcuAthlete.Profile p =
                IntervalsIcuAthlete.parse("{\"weight\": -5, \"icu_resting_hr\": -1}", "Biking");
        assertNull(p.weightKg);
        assertNull(p.restingHr);
        assertFalse(p.keys.contains(Constants.PREF_RIDER_WEIGHT));
    }
}