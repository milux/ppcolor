# PPColor
Send dominant color of 2nd screen as MIDI notes

## Idea
This tool measures the dominant color of the second screen and sends it as 3 MIDI notes to another device.

Combined with a MIDI-capable light control system is meant to provide an "Ambilight-like" experience for videos, presentations, etc.

The tool was tested with **ProPresenter** background videos, **Native Instruments Komplete Audio 6** sound/MIDI interface and a **grandMA2 onPC** light control system.

## Requirements
Java SE 8.0 or higher with `javax.sound.midi` available

## Usage
`java -jar ppcolor.jar`

## Output
The program will print 2 Color values for every measurement:

- The average color resulting from the top N/4 (default: N=400) values taken from the latest screenshot, weighted as (R+B+G)
- The resulting color after transformation to HSB, modification with saturation (S) to 1.0 (maximum saturation),
and normalization of the resulting color, such that the dominant RGB color channels(s) are 255.