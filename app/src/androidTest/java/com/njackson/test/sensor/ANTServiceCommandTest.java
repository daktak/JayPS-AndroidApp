package com.njackson.test.sensor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.application.IInjectionContainer;
import com.njackson.events.AntServiceCommand.AntStatus;
import com.njackson.sensor.ANTServiceCommand;
import com.njackson.sensor.IAnt;
import com.squareup.otto.Bus;

import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ANTServiceCommandTest extends AndroidTestCase {

    @Mock private Bus _mockBus;
    @Mock private SharedPreferences _mockPrefs;
    @Mock private Context _mockContext;
    @Mock private PackageManager _mockPm;
    @Mock private IAnt _mockAnt;

    private IInjectionContainer _noOpContainer = new IInjectionContainer() {
        @Override
        public void inject(Object object) {
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        _mockBus = mock(Bus.class);
        _mockPrefs = mock(SharedPreferences.class);
        _mockContext = mock(Context.class);
        _mockPm = mock(PackageManager.class);
        _mockAnt = mock(IAnt.class);
        when(_mockPrefs.getString(anyString(), anyString())).thenReturn("");
        when(_mockContext.getPackageManager()).thenReturn(_mockPm);
    }

    private ANTServiceCommand buildSut() throws Exception {
        ANTServiceCommand sut = new ANTServiceCommand();
        setField(sut, "_bus", _mockBus);
        setField(sut, "_sharedPreferences", _mockPrefs);
        setField(sut, "_applicationContext", _mockContext);
        setField(sut, "_ant", _mockAnt);
        return sut;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SmallTest
    public void test_execute_does_not_start_when_no_sensors_paired() throws Exception {
        ANTServiceCommand sut = buildSut();

        sut.execute(_noOpContainer);

        verify(_mockBus, never()).register(any(Object.class));
        verify(_mockAnt, never()).start(anySet(), any(), any());
    }

    @SmallTest
    public void test_execute_starts_when_sensor_paired_and_ant_available() throws Exception {
        when(_mockPrefs.getString("ant_address1", "")).thenReturn("123");
        when(_mockPm.getPackageInfo("com.dsi.ant", 0)).thenReturn(new PackageInfo());
        ANTServiceCommand sut = buildSut();

        sut.execute(_noOpContainer);

        verify(_mockBus).register(sut);
        verify(_mockAnt).start(anySet(), eq(_mockBus), eq(_noOpContainer));
        verify(_mockBus).post(any(AntStatus.class));
    }

    @SmallTest
    public void test_execute_does_not_start_when_ant_service_missing() throws Exception {
        when(_mockPrefs.getString("ant_address1", "")).thenReturn("123");
        when(_mockPm.getPackageInfo("com.dsi.ant", 0)).thenThrow(new PackageManager.NameNotFoundException());
        ANTServiceCommand sut = buildSut();

        sut.execute(_noOpContainer);

        verify(_mockBus, never()).register(any(Object.class));
        verify(_mockAnt, never()).start(anySet(), any(), any());
    }
}
