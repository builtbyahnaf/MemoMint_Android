package dev.builtbyahnaf.memomint;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests executing within the real Android OS runtime (device or emulator).
 *
 * <h2>Purpose & Scope (Section 25.7)</h2>
 * <p>Unlike host JVM unit tests, instrumented tests run with access to Android platform services,
 * system framework classes (e.g., {@link Context}, {@link android.content.ContentResolver}), and
 * real native SQLite binaries.</p>
 *
 * <h2>Integration Boundaries</h2>
 * <p>These tests verify that:
 * <ul>
 *   <li>The target Android application package is correctly configured and resolved by the OS.</li>
 *   <li>Contextual application initialization functions as expected without classloading or namespace mismatches.</li>
 * </ul>
 *
 * @see <a href="http://d.android.com/tools/testing">Android Testing Documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    /**
     * Verifies that the instrumented application context resolves to MemoMint's target package namespace.
     *
     * <p>Ensures that the instrumentation runner operates against {@code dev.builtbyahnaf.memomint}
     * rather than an unconfigured test artifact or orphaned process.</p>
     */
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Target package name must match dev.builtbyahnaf.memomint",
                "dev.builtbyahnaf.memomint", appContext.getPackageName());
    }
}