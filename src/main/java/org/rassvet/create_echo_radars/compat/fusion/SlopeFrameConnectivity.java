package org.rassvet.create_echo_radars.compat.fusion;

/**
 * Shape-only rules for the two triangular Copycats slopes. Keeping these
 * rules separate from Fusion makes every orientation straightforward to test.
 */
public final class SlopeFrameConnectivity {
    private SlopeFrameConnectivity() {}

    public enum Kind {
        REGULAR,
        VERTICAL
    }

    public enum HorizontalFacing {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    public enum Face {
        DOWN,
        UP,
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    public record Slope(Kind kind, HorizontalFacing facing) {}

    /**
     * Copycats creates the triangular end caps from the source cube's east and
     * west faces. A regular slope rotates those caps around Y; a vertical
     * slope first rotates them onto the top and bottom faces.
     */
    public static boolean isTriangularEndFace(Slope slope, Face face) {
        if (slope.kind() == Kind.VERTICAL) {
            return face == Face.UP || face == Face.DOWN;
        }
        boolean widthIsX = slope.facing() == HorizontalFacing.NORTH
                || slope.facing() == HorizontalFacing.SOUTH;
        return widthIsX
                ? face == Face.EAST || face == Face.WEST
                : face == Face.NORTH || face == Face.SOUTH;
    }
}
