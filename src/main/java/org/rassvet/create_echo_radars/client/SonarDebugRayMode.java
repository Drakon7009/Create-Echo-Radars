package org.rassvet.create_echo_radars.client;

public enum SonarDebugRayMode {
    OFF(false, false),
    MAIN(true, false),
    BEFORE_BLOCK(false, true),
    ALL(true, true);

    private final boolean main;
    private final boolean refinement;

    SonarDebugRayMode(boolean main, boolean refinement) {
        this.main = main;
        this.refinement = refinement;
    }

    public boolean shows(boolean refinementRay) {
        return refinementRay ? refinement : main;
    }

    public boolean tracesRefinements() {
        return refinement;
    }

    public boolean tracesAnything() {
        return main || refinement;
    }
}
