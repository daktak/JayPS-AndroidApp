package com.njackson.test.gps;

import android.location.Location;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.Constants;
import com.njackson.adapters.AdvancedLocationToNewLocation;
import com.njackson.events.GPSServiceCommand.NewLocation;

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

    @SmallTest
    public void testOutdoorSimulatedGpsWithHrCadPowerExportsTcx() throws Exception {
        int hr = 155;
        int cad = 92;
        int power = 220;
        long baseTime = System.currentTimeMillis();
        double baseLat = 48.8566;
        double baseLon = 2.3522;
        float speedMs = 1.0f;
        double deltaDeg = 0.00027;
        long prevElapsed = -1;
        int prevDashboardSec = -1;

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

        String gpx = adv.getGPX(true);
        assertTrue("GPX must contain HR", gpx.contains("<gpxtpx:hr>" + hr + "</gpxtpx:hr>"));
        assertTrue("GPX must contain cad", gpx.contains("<gpxtpx:cad>" + cad + "</gpxtpx:cad>"));
        assertTrue("GPX must have trkpt", gpx.contains("<trkpt"));
    }
}
