package com.njackson.sensor;

import com.njackson.application.IInjectionContainer;
import com.squareup.otto.Bus;
import java.util.Set;

public interface IAnt {
    public void start(Set<Integer> ant_deviceNumbers, Bus bus, IInjectionContainer container);
    public void stop();
}
