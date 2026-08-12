package org.rassvet.create_echo_radars.compat.cbcmoreshells;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbcmsTorpedoTypesTest {
    @Test
    void recognizesCannonAndTubeTorpedoHierarchy() {
        assertTrue(CbcmsTorpedoTypes.matchesHierarchy(List.of(
                "com.cainiao1053.cbcmoreshells.munitions.torpedo_tube.primary_torpedo.PrimaryTorpedoProjectile",
                CbcmsTorpedoTypes.CANNON_TORPEDO_BASE)));
    }

    @Test
    void recognizesRackedTorpedoHierarchy() {
        assertTrue(CbcmsTorpedoTypes.matchesHierarchy(List.of(
                "com.cainiao1053.cbcmoreshells.munitions.racked_projectile.racked_torpedo.RackedTorpedoProjectile",
                CbcmsTorpedoTypes.RACKED_TORPEDO_BASE)));
    }

    @Test
    void ignoresOtherCbcmsProjectiles() {
        assertFalse(CbcmsTorpedoTypes.matchesHierarchy(List.of(
                "com.cainiao1053.cbcmoreshells.munitions.big_cannon.apbc_shell.APBCShellProjectile",
                "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile")));
    }
}
