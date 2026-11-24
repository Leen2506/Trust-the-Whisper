# Trust the Whisper

## Project Overview
Trust the Whisper is a single-player horror maze game that combines
memory, reaction speed, and decision-making. The player must follow
whispered directions, avoid the ghost, and reach the exit before
losing all three lives.

The game uses a physical wireless controller that communicates
with a Java application through ZigBee.

## Hardware Components
- LilyPad Arduino
- Two XBee modules
- XBee Explorer
- Four touch sensors
- IR sensor
- RGB LED
- Passive buzzer
- Connecting wires
- Breadboard 

## Software Requirements
- Java Development Kit (JDK)
- Arduino IDE
- XCTU
- jSerialComm 2.11.0

## Hardware Connections

| Component | LilyPad Pin |
|---|---|
| Forward Touch Sensor | D10 |
| Backward Touch Sensor | D13 |
| Left Touch Sensor | A0 |
| Right Touch Sensor | D12 |
| IR Sensor | A1 |
| RGB LED (Red) | D5 |
| RGB LED (Green) | D6 |
| Passive Buzzer | D11 |
| XBee DOUT | D2 |
| XBee DIN | D3 |

## Setup Instructions

### 1. Arduino Setup
1. Connect the components according to the pin table.
2. Open `TrustTheWhisper.ino` in Arduino IDE.
3. Select the correct LilyPad board and port.
4. Upload the code to the LilyPad.
5. Power on the controller.

### 2. XBee Setup
1. Configure both XBee modules using XCTU.
2. Set the baud rate to 9600.
3. Connect the receiver XBee to the computer using the XBee Explorer.
4. Close XCTU and the Arduino Serial Monitor.

### 3. Java Setup
1. Download or clone the repository.
2. Open the `java` folder.
3. Run `run.bat` for Windows or `sh run.sh` for macOS/Linux.
4. Select the serial port connected to the XBee Explorer.
5. Start the game.

Keyboard simulation mode is also available for testing without
the physical controller.

## How to Play
- Use the four touch sensors to navigate the maze.
- Green LED: Follow the helper's directions.
- Red LED: Reverse the given directions.
- When the ghost approaches, cover the IR hiding sensor.
- Do not touch any directional sensors while hiding.
- Reach the exit before losing all three lives.

## Game Features
- Hidden maze with a visible trail
- Whispered voice instructions
- Helper possession mechanic
- Ghost and hiding encounters
- Increasing difficulty
- Horror sound effects
- Wireless physical controller
