package com.njackson.test.sensor;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.application.AppComponent;
import com.njackson.application.PebbleBikeApplication;
import com.njackson.sensor.ANTServiceCommand;

import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Guards the regression where PebbleBikeApplication.inject() had no branch for
 * ANTServiceCommand, so every start of MainService threw
 * "Cannot inject class com.njackson.sensor.ANTServiceCommand".
 */
public class ANTServiceCommandInjectionTest extends AndroidTestCase {

    @Mock private AppComponent _mockComponent;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        _mockComponent = mock(AppComponent.class);
    }

    @SmallTest
    public void test_application_injects_ant_service_command() throws Exception {
        PebbleBikeApplication app = new PebbleBikeApplication();
        setField(app, "component", _mockComponent);

        ANTServiceCommand sut = new ANTServiceCommand();
        // This used to throw IllegalArgumentException ("Cannot inject ...").
        app.inject(sut);

        // The dispatch must route ANTServiceCommand to the component injector.
        verify(_mockComponent).inject(sut);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
