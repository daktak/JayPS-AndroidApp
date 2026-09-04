package com.njackson.test.gps;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.Constants;
import com.njackson.adapters.AdvancedLocationToNewLocation;
import com.njackson.events.GPSServiceCommand.NewLocation;

import fr.jayps.android.AdvancedLocation;

public class IndoorPowermeterTest extends AndroidTestCase {

    private AdvancedLocation adv;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        adv = new AdvancedLocation(getContext());
        adv.setIndoor(true);
        adv.setSaveLocation(true);
        adv.resetGPX();
    }

    @Override
    protected void tearDown() throws Exception {
        adv.resetGPX();
        super.tearDown();
    }

    @SmallTest
    public void testIndoorPowermeterSpeedAndDistancePresentOnDashboard() throws Exception {
        int hr = 145;
        int cad = 85;
        int power = 180;
        long now = System.currentTimeMillis();

        float estSpeed = estimateSpeedFromPower(power);
        assertTrue("estimated speed from power must be >0", estSpeed > 0f);

        adv.setSensorSpeed(estSpeed, now);
        adv.updateIndoor(hr, cad, power, now);
        adv.saveCurrentLocationAtInterval(now);

        for (int i = 1; i <= 5; i++) {
            long t = now + i * 1000L;
            adv.setSensorSpeed(estSpeed, t);
            adv.updateIndoor(hr + i, cad, power, t);
            adv.saveCurrentLocationAtInterval(t);
        }

        assertTrue("speed must be >0 in indoor+powermeter", adv.getSpeed() > 0f);
        assertTrue("distance must be >0 in indoor+powermeter", adv.getDistance() > 0f);
        assertTrue("elapsed must be >0", adv.getElapsedTime() > 0L);
        assertTrue("totalElapsed must be >0", adv.getTotalElapsedTime() > 0L);
        assertTrue("average speed must be >0", adv.getAverageSpeed() > 0f);

        NewLocation nl = new AdvancedLocationToNewLocation(adv, 0, 0, Constants.METRIC);
        assertTrue("dashboard distance must be >0", nl.getDistance() > 0f);
        assertTrue("dashboard speed must be >0", nl.getSpeed() > 0f);
        assertTrue("dashboard avgSpeed must be >0", nl.getAverageSpeed() > 0f);
    }

    @SmallTest
    public void testIndoorSpeedSensorWithWheelSizeCalculatesSpeedDistanceAndExports() throws Exception {
        int hr = 142;
        int cad = 88;
        int power = 0;
        int wheelSizeMm = 2133;
        int wheelRpm = 90;
        float sensorSpeed = (float) (wheelSizeMm / 1000.0 * wheelRpm / 60.0);
        assertTrue("wheel sensor speed must be >0", sensorSpeed > 0f);

        getContext().getSharedPreferences("PREFS", 0).edit().putString("PREF_BLE_CSC_WHEEL_SIZE", String.valueOf(wheelSizeMm)).commit();

        long now = System.currentTimeMillis();
        adv.setSensorSpeed(sensorSpeed, now);
        adv.updateIndoor(hr, cad, power, now);
        adv.saveCurrentLocationAtInterval(now);
        for (int i = 1; i <= 5; i++) {
            long t = now + i * 1000L;
            adv.setSensorSpeed(sensorSpeed, t);
            adv.updateIndoor(hr, cad, power, t);
            adv.saveCurrentLocationAtInterval(t);
        }

        assertTrue("speed from wheel sensor must be >0", adv.getSpeed() > 0f);
        assertEquals("speed must match wheelSize/rpm", sensorSpeed, adv.getSpeed(), 0.01f);
        assertTrue("distance must be >0 from wheel sensor", adv.getDistance() > 0f);
        assertTrue("distance approx speed*5s", adv.getDistance() > sensorSpeed * 4.5f && adv.getDistance() < sensorSpeed * 5.5f);
        assertTrue("elapsed must be >0", adv.getElapsedTime() > 0L);
        assertTrue("totalElapsed must be >0", adv.getTotalElapsedTime() > 0L);

        NewLocation nl = new AdvancedLocationToNewLocation(adv, 0, 0, Constants.METRIC);
        assertTrue("dashboard speed must be >0", nl.getSpeed() > 0f);
        assertTrue("dashboard distance must be >0", nl.getDistance() > 0f);

        String tcx = adv.getTCX("Biking");
        assertTrue("TCX must contain HR", tcx.contains("<HeartRateBpm><Value>" + hr + "</Value></HeartRateBpm>"));
        assertTrue("TCX must contain cadence", tcx.contains("<Cadence>" + cad + "</Cadence>"));
        assertTrue("TCX must contain Speed from wheel", tcx.contains("<ns3:Speed>"));
        assertTrue("TCX must contain DistanceMeters from wheel", tcx.contains("<DistanceMeters>"));
        assertTrue("TCX speed value must match sensor", tcx.contains(String.valueOf(sensorSpeed).substring(0, 3)));

        String gpx = adv.getGPX(true);
        assertTrue("GPX must contain HR", gpx.contains("<gpxtpx:hr>" + hr + "</gpxtpx:hr>"));
        assertTrue("GPX must contain cad", gpx.contains("<gpxtpx:cad>" + cad + "</gpxtpx:cad>"));
    }

    @SmallTest
    public void testIndoorExportTcxPopulatedWithHrCadPower() throws Exception {
        int hr = 150;
        int cad = 90;
        int power = 200;
        long now = System.currentTimeMillis();

        float estSpeed = estimateSpeedFromPower(power);
        adv.setSensorSpeed(estSpeed, now);
        adv.updateIndoor(hr, cad, power, now);
        adv.saveCurrentLocationAtInterval(now);
        for (int i = 1; i <= 3; i++) {
            long t = now + i * 1000L;
            adv.setSensorSpeed(estSpeed, t);
            adv.updateIndoor(hr, cad, power, t);
            adv.saveCurrentLocationAtInterval(t);
        }

        String tcx = adv.getTCX("Biking");
        assertTrue("TCX must contain HR", tcx.contains("<HeartRateBpm><Value>" + hr + "</Value></HeartRateBpm>"));
        assertTrue("TCX must contain cadence", tcx.contains("<Cadence>" + cad + "</Cadence>"));
        assertTrue("TCX must contain power", tcx.contains("<ns3:Watts>" + power + "</ns3:Watts>"));
        assertTrue("TCX must contain Speed", tcx.contains("<ns3:Speed>"));
        assertTrue("TCX must contain DistanceMeters", tcx.contains("<DistanceMeters>"));
        assertTrue("TCX distance value must be >0", adv.getDistance() > 0f);
    }

    private float estimateSpeedFromPower(int power) {
        if (power <= 0) return 0f;
        final double m = 85.0;
        final double g = 9.81;
        final double Crr = 0.005;
        final double rho = 1.225;
        final double CdA = 0.3;
        final double roll = Crr * m * g;
        final double aero = 0.5 * rho * CdA;
        double low = 0.0;
        double high = 30.0;
        double v = 0.0;
        for (int i = 0; i < 60; i++) {
            v = (low + high) / 2;
            double p = roll * v + aero * v * v * v;
            if (p > power) high = v;
            else low = v;
        }
        return (float) v;
    }
}
