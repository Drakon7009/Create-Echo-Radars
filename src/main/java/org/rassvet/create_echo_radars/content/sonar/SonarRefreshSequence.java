package org.rassvet.create_echo_radars.content.sonar;

import java.util.List;

/** Keeps the displayed sweep independent of server scan completion. */
public record SonarRefreshSequence(SonarFrame refreshing) {
    public static SonarRefreshSequence select(List<SonarFrame> frames, long fullyRefreshedEpoch) {
        SonarFrame refreshing = null;
        for (SonarFrame frame : frames) {
            if (frame.epoch() > fullyRefreshedEpoch
                    && (refreshing == null || frame.epoch() < refreshing.epoch())) {
                refreshing = frame;
            }
        }
        return new SonarRefreshSequence(refreshing);
    }

    public boolean pending(SonarFrame frame) {
        return refreshing != null && frame.epoch() > refreshing.epoch();
    }
}
