# Create: Echo Radars

An add-on for Minecraft 1.21.1 and NeoForge that adds underwater sonars and ways to display their signals through Create: Radars networks. Only the Mechanical Imaging Sonar needs rotational power from Create; the other sonars do not.

## Features

- **Forward-looking Sonar** scans the area ahead and displays echoes on a Create: Radars monitor.
- **Echo Sounder** scans the space below it.
- **Side-scan Sonar** scans both sides and displays a history of its passes.
- **Mechanical Imaging Sonar** rotates to scan its surroundings. It needs rotational power supplied to the shaft on its base.
- Sonar range, horizontal and vertical field of view, and tilt are configurable. A wider field of view can reduce the available range. The emitter must be submerged in water.
- Sonars connect to Create: Radars Network Filterers and monitors through Data Links. Pings reflect off obstacles and leave acoustic shadows; the monitor displays scan returns and detected targets.
- **Copper and Iron Sonar Glass** form connected windows that display sonar data. One window can receive one signal directly or combine up to four signals through a **Sonar Signal Summator**. The summator must be placed within 16 blocks of the selected window.
- Client settings control the monitor palette, gain, speckle, and sonar glass display. Server settings control beam counts, scan timing, range, and performance limits.

## Required dependencies

| Mod | Requirement |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | For Minecraft 1.21.1; this project builds against 21.1.235 |
| Create | 6.0.10 or newer, but below 6.1.0 |
| Create: Radars | 0.4.9.4 or newer, but below 6.0.0 |
| YetAnotherConfigLib (YACL) | 3.8.2 or newer on the client, for the configuration screen |


## Optional compatibility

Install these mods only if you want the corresponding features:

| Mod | Added compatibility |
| --- | --- |
| Sable 1.2.1+ | Scans moving contraptions and keeps links connected when they move. |
| CBC Military Supplement 2.1.4 or newer, but below 2.2.0 | Supports guidance for its torpedoes equipped with a Create: Radars Guided Fuze. |
| Veil 4.0.0+ | Adds an alternative sonar glass renderer; a compatible renderer is used without Veil. |
| Fusion 1.2.12+ | Provides connected textures for sonar glass frames. |
| Copycats | Allows sonar glass to be used as a material for compatible Copycats shapes. |


## Credits

- **Drakon7009** — mod author.
- **chakchak777** and **ken_flish** — textures.
