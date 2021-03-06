# PPColor
Send dominant color of 2nd screen as MIDI notes

## Idea
This tool measures the dominant color of the second screen and sends it as 3 MIDI notes to another device.

Combined with a MIDI-capable light control system this is meant to provide an "Ambilight-like" experience for videos, presentations, etc.

The tool was tested with **ProPresenter** background videos, **Native Instruments Komplete Audio 6** sound/MIDI interface and a **grandMA2 onPC** light control system.

## Requirements
JDK 8.0 or higher with `javax.sound.midi` available

## Usage
Build with `./gradlew jar proguard` or download a release JAR

Run with `java -jar ppcolor-<version>.jar`

## Output
The program will print 2 Color values for every measurement:

- The average color resulting from the top N (default: N <= 100) values taken from the latest screenshot, weighted with Saturation x Brightness
- The resulting color after transformation to HSB and modification of saturation (S) and brightness (B) to 1.0 (maximum saturation/brightness)