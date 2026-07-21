package org.rassvet.create_echo_radars.content.sonar;

public final class SonarKinematics {
    public static final float MIN_PITCH = -10f;
    public static final float MAX_PITCH = 10f;

    private SonarKinematics() {}

    public static boolean insideCone(double x, double y, double z, double forwardX, double forwardZ,
                                     int horizontalSector, float range) {
        double flatLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (flatLength < 1.0e-5) return false;
        double forward = (x * forwardX + z * forwardZ) / flatLength;
        double side = (x * -forwardZ + z * forwardX) / flatLength;
        return insideCone(new SonarMath.Projection(forward, side, y,
                Math.sqrt(x * x + y * y + z * z)), horizontalSector, range);
    }

    public static boolean insideCone(SonarMath.Projection projection,
                                     int horizontalSector, float range) {
        return insideCone(projection, horizontalSector, 20, range);
    }

    public static boolean insideCone(SonarMath.Projection projection,
                                     int horizontalSector, int verticalSector, float range) {
        if (projection.range() > range) return false;
        if (projection.range() < 1.0e-5) return true;
        double horizontal = Math.sqrt(projection.forward() * projection.forward()
                + projection.side() * projection.side());
        if (horizontal < 1.0e-5) return false;
        double yaw = Math.toDegrees(Math.atan2(Math.abs(projection.side()), projection.forward()));
        double pitch = Math.toDegrees(Math.atan2(projection.up(), horizontal));
        return yaw <= horizontalSector / 2f && Math.abs(pitch) <= verticalSector / 2f;
    }

    public static float reflectedIntensity(float incidence, float normalizedDistance) {
        /*
         * A hard incidence threshold makes the +/-10 degree elevation beams
         * effectively blind to the sea floor: their Y incidence is only
         * 0.09-0.17. Keep grazing returns weaker, but never erase a confirmed
         * collision solely because it hit a shallow face.
         */
        float angle = clamp(incidence, 0, 1);
        float reflection = 0.10f + 0.90f * (float) Math.pow(angle, 0.65);
        float distance = clamp(normalizedDistance, 0, 1);
        float attenuation = 0.24f + 0.76f * (float) Math.exp(-1.35f * distance);
        return clamp(reflection * attenuation, 0, 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
