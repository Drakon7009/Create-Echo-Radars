package org.rassvet.create_echo_radars.compat.cbcmoreshells;

/**
 * Identifies CBC Military Supplement torpedoes without linking Echo Radars to
 * CBCMS classes. Keeping the names as strings allows the compatibility code to
 * remain loadable when CBCMS is not installed.
 */
public final class CbcmsTorpedoTypes {
    static final String CANNON_TORPEDO_BASE =
            "com.cainiao1053.cbcmoreshells.munitions.big_cannon.AbstractCannonTorpedoProjectile";
    static final String RACKED_TORPEDO_BASE =
            "com.cainiao1053.cbcmoreshells.munitions.racked_projectile.AbstractRackedTorpedoProjectile";
    private static final ClassValue<Boolean> SUPPORTED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> projectileClass) {
            for (Class<?> type = projectileClass; type != null; type = type.getSuperclass()) {
                if (isTorpedoBase(type.getName())) return true;
            }
            return false;
        }
    };

    private CbcmsTorpedoTypes() {}

    public static boolean isSupported(Class<?> projectileClass) {
        return SUPPORTED.get(projectileClass);
    }

    static boolean matchesHierarchy(Iterable<String> hierarchy) {
        for (String className : hierarchy) {
            if (isTorpedoBase(className)) return true;
        }
        return false;
    }

    private static boolean isTorpedoBase(String className) {
        return CANNON_TORPEDO_BASE.equals(className) || RACKED_TORPEDO_BASE.equals(className);
    }
}
