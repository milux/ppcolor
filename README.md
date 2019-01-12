# PPColor
Send dominant colors of 2nd screen as MIDI notes

## Idea
This tool measures the dominant colors of the second screen and sends it as 3 MIDI notes to another device.

Combined with a MIDI-capable light control system this is meant to provide an "Ambilight-like" experience for videos, presentations, etc.

The tool was tested with **ProPresenter** background videos, **Native Instruments Komplete Audio 6** sound/MIDI interface and a **grandMA2 onPC** light control system.

## Requirements
- Java 8.0 or higher with `javax.sound.midi` available (default for common JREs)
- Latest VLC 2.2.x in directory "vlc" in Project root or along with the JAR file

## Usage
### Quick Run
Directly run with command `./gradlew run`

### Build
Build JAR with command `./gradlew jar`

Run with command `java -jar ppcolor-<version>.jar`

## Output
Per default, the program outputs information about detected screen devices and VLC configuration, VLC errors
and **Hue values of the colors** that have been determined and are being sent via the MIDI output.