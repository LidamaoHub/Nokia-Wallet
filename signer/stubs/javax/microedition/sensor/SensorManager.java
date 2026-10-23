package javax.microedition.sensor;
/** Compile-only JSR-256 signatures. Excluded from the handset JAR. */
public final class SensorManager {
    private SensorManager() { }
    public static SensorInfo[] findSensors(String quantity, String contextType) { return null; }
}
