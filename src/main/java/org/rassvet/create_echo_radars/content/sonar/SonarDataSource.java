package org.rassvet.create_echo_radars.content.sonar;

import com.happysg.radar.block.radar.track.RadarTrack;

import java.util.Collection;
public interface SonarDataSource {
    Collection<RadarTrack> getTracks();

    int getSonarRange();

    int getHorizontalSector();

    int getVerticalSector();

    SonarType getSonarType();

}
