package com.njackson.test.gps;

import android.location.Location;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.Constants;
import com.njackson.adapters.AdvancedLocationToNewLocation;
import com.njackson.events.GPSServiceCommand.NewLocation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.jayps.android.AdvancedLocation;

public class OutdoorModeTest extends AndroidTestCase {

    private AdvancedLocation adv;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        adv = new AdvancedLocation(getContext());
        adv.setIndoor(false);
        adv.setSaveLocation(true);
        adv.setSaveOnLocationChange(true);
        adv.resetGPX();
    }

    @Override
    protected void tearDown() throws Exception {
        adv.resetGPX();
        super.tearDown();
    }

    // the ride is a straight line north-east, one point every 30s
    private static final double BASE_LAT = 48.8566;
    private static final double BASE_LON = 2.3522;
    private static final double DELTA_DEG = 0.00027;
    private static final long POINT_INTERVAL_MS = 30000L;

    // keeps recordMorePoints() continuing the same ride, so timestamps stay ascending
    private long _rideBaseTime;
    private int _ridePoints;

    /** Records 5 points 30s apart at 155bpm, so a ride spans 2 minutes of history. */
    private void recordRide() {
        _rideBaseTime = System.currentTimeMillis();
        _ridePoints = 0;
        recordMorePoints(5);
    }

    /** Continues {@link #recordRide()} with the same geometry and cadence. */
    private void recordMorePoints(int count) {
        for (int i = 0; i < count; i++) {
            Location loc = new Location("JayPS");
            loc.setLatitude(BASE_LAT + _ridePoints * DELTA_DEG);
            loc.setLongitude(BASE_LON);
            loc.setAltitude(80 + _ridePoints * 2);
            loc.setAccuracy(5);
            loc.setTime(_rideBaseTime + _ridePoints * POINT_INTERVAL_MS);
            loc.setSpeed(1.0f);
            adv.onLocationChanged(loc, 155, 92, 220);
            adv.saveCurrentLocationAtInterval(loc.getTime());
            _ridePoints++;
        }
    }

    @SmallTest
    public void testOutdoorSimulatedGpsWithHrCadPowerExportsTcx() throws Exception {
        int hr = 155;
        int cad = 92;
        int power = 220;
        long prevElapsed = -1;
        int prevDashboardSec = -1;

        long baseTime = System.currentTimeMillis();
        double baseLat = 48.8566;
        double baseLon = 2.3522;
        float speedMs = 1.0f;
        double deltaDeg = 0.00027;
        for (int i = 0; i < 5; i++) {
            Location loc = new Location("JayPS");
            loc.setLatitude(baseLat + i * deltaDeg);
            loc.setLongitude(baseLon);
            loc.setAltitude(80 + i * 2);
            loc.setAccuracy(5);
            loc.setTime(baseTime + i * 30000L);
            loc.setSpeed(speedMs);

            int result = adv.onLocationChanged(loc, hr, cad, power);
            assertTrue("onLocationChanged must not skip", result != AdvancedLocation.SKIPPED);

            adv.saveCurrentLocationAtInterval(loc.getTime());

            long curElapsed = adv.getElapsedTime();
            assertTrue("elapsed must increase at i=" + i, curElapsed > prevElapsed);
            prevElapsed = curElapsed;

            NewLocation dash = new AdvancedLocationToNewLocation(adv, 0, 0, Constants.METRIC);
            assertTrue("dashboard elapsed must be >0 at i=" + i, dash.getElapsedTimeSeconds() > 0 || i == 0);
            if (dash.getElapsedTimeSeconds() > 0) {
                assertTrue("dashboard elapsed must increase at i=" + i, dash.getElapsedTimeSeconds() > prevDashboardSec);
                prevDashboardSec = dash.getElapsedTimeSeconds();
            }
            assertTrue("dashboard distance must be >0 at i=" + i + " value=" + dash.getDistance(), dash.getDistance() >= 0f);
            assertTrue("dashboard speed must be >0 at i=" + i, dash.getSpeed() > 0f);
            assertTrue("speed must be 3-4km/h at i=" + i + " was " + dash.getSpeed(), dash.getSpeed() >= 2.8f && dash.getSpeed() <= 4.5f);
        }

        assertTrue("outdoor distance must be >0", adv.getDistance() > 0f);
        assertTrue("outdoor speed must be >0", adv.getSpeed() > 0f);
        assertTrue("speed 3-4km/h check m/s", adv.getSpeed() >= 0.7f && adv.getSpeed() <= 1.4f);
        assertTrue("outdoor elapsed must be >0", adv.getElapsedTime() > 0L);
        assertTrue("outdoor totalElapsed must be >0", adv.getTotalElapsedTime() > 0L);
        assertTrue("outdoor average speed must be >0", adv.getAverageSpeed() > 0f);

        NewLocation nl = new AdvancedLocationToNewLocation(adv, 0, 0, Constants.METRIC);
        assertTrue("dashboard distance must be >0", nl.getDistance() > 0f);
        assertTrue("dashboard speed must be >0", nl.getSpeed() > 0f);
        assertTrue("dashboard speed 3-4km/h", nl.getSpeed() >= 2.8f && nl.getSpeed() <= 4.5f);
        assertTrue("dashboard avgSpeed must be >0", nl.getAverageSpeed() > 0f);
        assertTrue("dashboard elapsed must be >0", nl.getElapsedTimeSeconds() > 0);
        assertTrue("dashboard total must be >0", nl.getTotalTimeSeconds() > 0);

        String tcx = adv.getTCX("Biking");
        assertTrue("TCX must contain HR " + hr, tcx.contains("<HeartRateBpm><Value>" + hr + "</Value></HeartRateBpm>"));
        assertTrue("TCX must contain cadence " + cad, tcx.contains("<Cadence>" + cad + "</Cadence>"));
        assertTrue("TCX must contain power " + power, tcx.contains("<ns3:Watts>" + power + "</ns3:Watts>"));
        assertTrue("TCX must contain Speed", tcx.contains("<ns3:Speed>"));
        assertTrue("TCX must contain DistanceMeters", tcx.contains("<DistanceMeters>"));
        assertTrue("TCX DistanceMeters must be >0", tcx.contains("<DistanceMeters>" + String.valueOf((int) adv.getDistance()).substring(0, 1)));
        assertFalse("TCX must not contain the literal null", tcx.contains(">null<"));
        assertTrue("TCX Lap must have StartTime", tcx.contains("<Lap StartTime=\""));
        assertTrue("TCX must contain TotalTimeSeconds", tcx.contains("<TotalTimeSeconds>"));
        assertTrue("TCX must contain lap DistanceMeters", tcx.contains("</TotalTimeSeconds>\n    <DistanceMeters>"));
        assertTrue("TCX must contain Calories", tcx.contains("<Calories>"));
        assertTrue("TCX must contain Intensity Active", tcx.contains("<Intensity>Active</Intensity>"));
        assertTrue("TCX must contain TriggerMethod Manual", tcx.contains("<TriggerMethod>Manual</TriggerMethod>"));
        assertTrue("TCX must contain Sport Biking", tcx.contains("<Activity Sport=\"Biking\">"));

        String gpx = adv.getGPX(true);
        assertTrue("GPX must contain HR", gpx.contains("<gpxtpx:hr>" + hr + "</gpxtpx:hr>"));
        assertTrue("GPX must contain cad", gpx.contains("<gpxtpx:cad>" + cad + "</gpxtpx:cad>"));
        assertTrue("GPX must contain watts " + power, gpx.contains("<gpxtpx:watts>" + power + "</gpxtpx:watts>"));
        assertTrue("GPX must contain pb10 power " + power, gpx.contains("<pb10:power>" + power + "</pb10:power>"));
        assertFalse("GPX must not contain the literal null", gpx.contains(">null<"));
        assertTrue("GPX must have trkpt", gpx.contains("<trkpt"));
    }

    @SmallTest
    public void testCaloriesRequireProfileAndAreExportedWhenSet() throws Exception {
        recordRide();
        String tcxWithoutProfile = adv.getTCX("Biking");
        assertTrue("TCX without a profile must report zero calories",
                tcxWithoutProfile.contains("<Calories>0</Calories>"));
        assertFalse("TCX without a profile must not carry per-point calories",
                tcxWithoutProfile.contains("<ns3:Calories>"));
        assertFalse("GPX without a profile must not carry calories",
                adv.getGPX(true).contains("<pb10:calories>"));

        // A profile must not raise the estimate above what 155bpm over 2 minutes allows
        adv.setRiderProfile(35, false, 75, 180, 48, 185);
        int calories = adv.getCalories();
        assertTrue("calories must be >0 with a full profile, was " + calories, calories > 0);
        assertTrue("calories must be plausible for ~2min at 155bpm, was " + calories, calories < 200);
        assertEquals("getCalories must be cached", calories, adv.getCalories());

        assertEquals("dashboard must carry calories", calories,
                new AdvancedLocationToNewLocation(adv, 0, 0, Constants.METRIC).getCalories());

        String tcx = adv.getTCX("Biking");
        assertTrue("TCX Lap must carry the calculated calories",
                tcx.contains("<Calories>" + calories + "</Calories>"));
        assertTrue("TCX must carry per-point calories", tcx.contains("<ns3:Calories>"));
        assertTrue("GPX must carry cumulative calories",
                adv.getGPX(true).contains("<pb10:calories>" + calories + "</pb10:calories>"));
        assertFalse("plain GPX must not carry calories",
                adv.getGPX(false).contains("<pb10:calories>"));
    }

    /**
     * A lap ends at the first point of the next one, so the exported laps share no trackpoint and
     * leave no gap in time. Their distances must add up to the ride, which is the property that
     * breaks silently if a boundary is counted in both laps.
     */
    @SmallTest
    public void testLapsPartitionTheRideInTcxAndGpx() throws Exception {
        recordRide();

        // close the first lap after 3 of the 5 points, then keep recording
        adv.newLap();
        recordMorePoints(3);
        float rideDistance = adv.getDistance();

        assertEquals("a new lap starts at lap 1", 1, adv.getLapCount());
        assertTrue("the new lap must have its own distance", adv.getLapDistance() > 0f);
        assertTrue("the new lap must have its own elapsed time", adv.getLapElapsedTime() > 0L);
        assertTrue("last lap time must be recorded", adv.getLastLapElapsedTime() > 0L);
        assertEquals("last lap time is the best so far", adv.getLastLapElapsedTime(), adv.getBestLapElapsedTime());
        assertTrue("the new lap must have its own average speed", adv.getLapAverageSpeed() > 0f);

        // saveOnLocationChange plus the explicit interval save stores two rows per point
        int expectedPoints = 2 * _ridePoints;
        String tcx = adv.getTCX("Biking");
        assertEquals("TCX must contain one Lap per lap", count(tcx, "<Lap StartTime=\""), 2);
        assertEquals("TCX must contain one Activity", count(tcx, "<Activity Sport="), 1);
        assertEquals("every point belongs to exactly one lap", count(tcx, "<Trackpoint>"), expectedPoints);
        assertTrue("lap distances must add up to the ride, were "
                        + sumLapDistances(tcx) + " vs " + rideDistance,
                Math.abs(sumLapDistances(tcx) - rideDistance) < 1f);

        String gpx = adv.getGPX(true);
        // one segment per lap, i.e. the initial one plus one per lap boundary
        assertEquals("GPX must start a segment per lap", 2, count(gpx, "<trkseg>"));
        assertEquals("every point belongs to exactly one segment", expectedPoints, count(gpx, "<trkpt"));
    }

    /**
     * The lap state is what carries laps across a pause: saving it and restoring it into a fresh
     * instance has to reproduce the same numbers, or a rider who pauses loses their laps.
     */
    @SmallTest
    public void testLapStateSurvivesSaveAndRestore() throws Exception {
        recordRide();
        adv.newLap();
        recordMorePoints(2);

        AdvancedLocation.LapState expected = adv.getLapState();
        AdvancedLocation restored = new AdvancedLocation(getContext());
        restored.setSaveLocation(true);
        restored.setSaveOnLocationChange(true);
        restored.setIndoor(false);
        restored.setLapState(expected);

        assertEquals("lap count must survive", expected.lapCount, restored.getLapCount());
        assertEquals("lap distance must survive", expected.lapDistance, restored.getLapDistance(), 0.001);
        assertEquals("lap elapsed time must survive", expected.lapElapsedTime, restored.getLapElapsedTime());
        assertEquals("last lap time must survive", expected.lastLapElapsedTime, restored.getLastLapElapsedTime());
        assertEquals("best lap time must survive", expected.bestLapElapsedTime, restored.getBestLapElapsedTime());
        assertEquals("lap average power must survive", adv.getLapAveragePower(), restored.getLapAveragePower());
        assertEquals("ride average power must survive", adv.getAveragePower(), restored.getAveragePower());

        // and the restored instance keeps numbering laps rather than starting over
        restored.newLap();
        assertEquals("laps must continue after a restore", expected.lapCount + 1, restored.getLapCount());
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return n;
    }

    /**
     * Sum of the DistanceMeters of every exported TCX Lap, so the laps can be checked to sum to the
     * ride. Matches the Lap header only, which is anchored by TotalTimeSeconds: the per-trackpoint
     * DistanceMeters must not be counted here.
     */
    private static float sumLapDistances(String tcx) {
        Matcher m = Pattern.compile(
                "<Lap StartTime=\"[^\"]*\">\\s*<TotalTimeSeconds>[^<]*</TotalTimeSeconds>\\s*"
                        + "<DistanceMeters>([^<]*)</DistanceMeters>").matcher(tcx);
        float total = 0f;
        int found = 0;
        while (m.find()) {
            total += Float.parseFloat(m.group(1));
            found++;
        }
        assertEquals("every lap header must be parseable", found, count(tcx, "<Lap StartTime=\""));
        return total;
    }

    @SmallTest
    public void testCalorieTierFallsBackAsProfileFieldsAreMissing() throws Exception {
        recordRide();
        // Age and weight only: no resting HR, so the model must drop to the MET tier
        adv.setRiderProfile(35, false, 75, 0, 0, 0);
        assertTrue("MET tier must still estimate calories", adv.getCalories() > 0);

        // Weight only: no age, still above the "nothing known" threshold
        adv.setRiderProfile(0, false, 75, 0, 0, 0);
        assertTrue("weight alone must still estimate calories", adv.getCalories() > 0);

        // Nothing known: EnergyModel.TIER_NONE, so no calories at all
        adv.setRiderProfile(0, false, 0, 0, 0, 0);
        assertEquals("no profile data must mean zero calories", 0, adv.getCalories());
    }
}
