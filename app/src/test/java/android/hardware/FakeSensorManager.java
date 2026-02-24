// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

/**
 * A minimal, concrete SensorManager subclass for unit tests.
 *
 * <p>SensorManager's constructor is package-private so this class must reside in
 * the {@code android.hardware} package to call it.  All sensor-lookup methods
 * return {@code null} so that {@code StepSensorManager.getAvailableSensorType()}
 * resolves to {@link com.podometer.data.sensor.SensorType#NONE}, allowing
 * idempotency-guard tests to run on the JVM without a real Android device.
 *
 * <p>The {@link #registerListenerCallCount} field lets tests assert that
 * {@code registerListener} is NOT called when the guard fires.
 */
public class FakeSensorManager extends SensorManager {

    /** Number of times {@link #registerListener} was invoked. */
    public int registerListenerCallCount = 0;

    public FakeSensorManager() {
        // Package-private super constructor — accessible because this class is
        // in the same package (android.hardware).
        super();
    }

    @Override
    public Sensor getDefaultSensor(int type) {
        return null;
    }

    @Override
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs) {
        registerListenerCallCount++;
        return false;
    }

    @Override
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs, int maxReportLatencyUs) {
        registerListenerCallCount++;
        return false;
    }

    @Override
    public void unregisterListener(SensorEventListener listener) {
        // no-op stub
    }
}
