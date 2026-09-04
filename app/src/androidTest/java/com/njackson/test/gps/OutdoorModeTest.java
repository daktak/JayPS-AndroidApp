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

        for (int i = 0; i < 5; i++) {
            Location loc = new Location("JayPS");
            loc.setLatitude(baseLat + i * 0.001);
            loc.setLongitude(baseLon + i * 0.001);
            loc.setAltitude(80 + i * 2);
            loc.setAccuracy(5);
            loc.setTime(baseTime + i * 30000L);
            loc.setSpeed(3.5f);

            int result = adv.onLocationChanged(loc, hr, cad, power);
            assertTrue("onLocationChanged must not skip", result != AdvancedLocation.SKIPPED);

            adv.saveCurrentLocationAtInterval(loc.getTime());
        }

        assertTrue("outdoor distance must be >0", adv.getDistance() > 0f);
        assertTrue("outdoor speed must be >0", adv.getSpeed() > 0f);
        assertTrue("outdoor elapsed must be >0", adv.getElapsedTime() > 0L);
        assertTrue("outdoor totalElapsed must be >0", adv.getTotalElapsedTime() > 0L);
        assertTrue("outdoor average speed must be >0", adv.getAverageSpeed() > 0f);

        NewLocation nl = new AdvancedLocationToNewLocation(adv, 0, 0, Constants.METRIC);
        assertTrue("dashboard distance must be >0", nl.getDistance() > 0f);
        assertTrue("dashboard speed must be >0", nl.getSpeed() > 0f);
        assertTrue("dashboard avgSpeed must be >0", nl.getAverageSpeed() > 0f);

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
