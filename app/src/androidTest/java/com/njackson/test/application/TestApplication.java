package com.njackson.test.application;

import android.app.Application;
import android.content.Context;

/**
 * Minimal application used by MyInstrumentationTestRunner for instrumentation tests.
 * Injection is handled per-test (manual or Dagger 2), so no legacy ObjectGraph wiring here.
 */
public class TestApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Mockito's AndroidByteBuddyMockMaker needs a private, writable directory to store
        // generated classes. On API 29+ the implicit location is unavailable, so point it at
        // a private app directory (required before any mock is created).
        System.setProperty("org.mockito.android.target", getDir("mockito", Context.MODE_PRIVATE).getAbsolutePath());
    }
}
