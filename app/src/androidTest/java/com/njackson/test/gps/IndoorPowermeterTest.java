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
