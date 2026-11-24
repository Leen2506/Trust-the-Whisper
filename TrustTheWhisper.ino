/*
 * Scary Heartbeat Maze - wireless player controller (LilyPad Arduino + XBee)
 *
 * Parts : 4 touch sensors, IR sensor (hiding spot), RGB LED, passive buzzer
 * RGB   : green = helper is with you, red = helper is poisoned
 *
 * Sends to Java   : D:F D:B D:L D:R (touch)  |  I:1 / I:0 (IR)  |  HELLO
 * Gets from Java  : R C W P G g K N V X O ?  and  B<bpm> (heartbeat sound)
 * You can also type these commands in the Serial Monitor to test.
 */

#include <SoftwareSerial.h>

// ---------------- PINS ----------------
const int PIN_XBEE_RX = 2;     // <- XBee DOUT
const int PIN_XBEE_TX = 3;     // -> XBee DIN

const int  TOUCH_PINS[4]  = {10, 13, A0, 12};       // Forward, Backward, Left, Right
const char TOUCH_CODES[4] = {'F', 'B', 'L', 'R'};

const int PIN_IR = A1;         // IR sensor OUT (LOW when something is close)

const int PIN_RED = 5;         // RGB LED red
const int PIN_GREEN = 6;       // RGB LED green
const bool COMMON_ANODE = true; // LilyPad tri-color LED: LOW = on

const int PIN_BUZZER = 11;     // passive buzzer

SoftwareSerial xbee(PIN_XBEE_RX, PIN_XBEE_TX);

// ---------------- SEND A MESSAGE ----------------
void sendLine(const char *text) {
  xbee.print(text);            // to the game
  xbee.print('\n');
  Serial.println(text);        // copy for the Serial Monitor
}

// ---------------- RGB LED ----------------
void setLed(bool red, bool green) {
  digitalWrite(PIN_RED,   (red   != COMMON_ANODE) ? HIGH : LOW);
  digitalWrite(PIN_GREEN, (green != COMMON_ANODE) ? HIGH : LOW);
}

// ---------------- TOUCH SENSORS ----------------
// Metal touch sensors flicker while touched, so a touch "holds" for 60 ms.
unsigned long lastActive[4] = {0, 0, 0, 0};
bool wasTouched[4] = {false, false, false, false};
unsigned long lastSent = 0;

void readTouch(unsigned long now) {
  for (int i = 0; i < 4; i++) {
    if (digitalRead(TOUCH_PINS[i]) == HIGH) lastActive[i] = now;
    bool touched = lastActive[i] != 0 && now - lastActive[i] < 60;

    // send once per new touch, and ignore other touches for 300 ms
    if (touched && !wasTouched[i] && now - lastSent > 300) {
      char msg[4] = {'D', ':', TOUCH_CODES[i], '\0'};
      sendLine(msg);
      lastSent = now;
    }
    wasTouched[i] = touched;
  }
}

// ---------------- IR SENSOR (hiding spot) ----------------
bool hidden = false, lastReading = false;
unsigned long changedAt = 0;

void readIR(unsigned long now) {
  bool covered = digitalRead(PIN_IR) == LOW;
  if (covered != lastReading) { lastReading = covered; changedAt = now; }
  // must stay the same for 80 ms before we report it
  if (covered != hidden && now - changedAt >= 80) {
    hidden = covered;
    sendLine(hidden ? "I:1" : "I:0");
  }
}

// ---------------- BUZZER ----------------
// Each sound is a list of {frequency, milliseconds}. 0 = silence.
const int SND_CORRECT[][2] = {{1200, 60}, {1800, 90}};
const int SND_WRONG[][2]   = {{220, 220}, {150, 320}};
const int SND_POISON[][2]  = {{900, 110}, {800, 110}, {700, 110}, {600, 120}, {500, 140}, {400, 180}, {300, 300}};
const int SND_GHOST[][2]   = {{110, 300}, {0, 80}, {117, 300}, {0, 80}, {104, 450}};
const int SND_CAUGHT[][2]  = {{2000, 90}, {1800, 90}, {2100, 90}, {1700, 90}, {200, 450}};
const int SND_NEAR[][2]    = {{140, 90}};
const int SND_WIN[][2]     = {{523, 150}, {659, 150}, {784, 150}, {1047, 400}};
const int SND_LOSE[][2]    = {{392, 300}, {370, 300}, {349, 300}, {330, 700}};
#define PLAY(s) playSound(s, sizeof(s) / sizeof(s[0]))

const int (*sound)[2] = NULL;   // sound playing now (NULL = none)
int soundLen = 0, noteIndex = 0;
unsigned long noteEnds = 0;

int heartbeatBpm = 0;           // 0 = no heartbeat
unsigned long nextBeat = 0, dubAt = 0;
bool dubPending = false;

void startNote(unsigned long now) {
  int freq = sound[noteIndex][0], ms = sound[noteIndex][1];
  if (freq > 0) tone(PIN_BUZZER, freq, ms); else noTone(PIN_BUZZER);
  noteEnds = now + ms + 15;
}

void playSound(const int s[][2], int len) {
  sound = s; soundLen = len; noteIndex = 0;
  startNote(millis());
}

void updateBuzzer(unsigned long now) {
  if (sound != NULL) {                       // sound effects come first
    if (now >= noteEnds) {
      noteIndex++;
      if (noteIndex < soundLen) startNote(now);
      else { sound = NULL; noTone(PIN_BUZZER); }
    }
    return;
  }
  if (heartbeatBpm == 0) return;             // "lub-dub" heartbeat
  if (now >= nextBeat) {
    tone(PIN_BUZZER, 70, 45);
    dubPending = true;
    dubAt = now + 160;
    nextBeat = now + 60000UL / heartbeatBpm;
  }
  if (dubPending && now >= dubAt) {
    tone(PIN_BUZZER, 55, 40);
    dubPending = false;
  }
}

void stopSound() {
  sound = NULL;
  noTone(PIN_BUZZER);
}

// ---------------- COMMANDS FROM JAVA ----------------
void handleCommand(const char *cmd) {
  switch (cmd[0]) {
    case 'R': setLed(false, true); stopSound(); break;          // new game: green
    case 'P': setLed(true, false); PLAY(SND_POISON); break;     // helper poisoned: red
    case 'C': PLAY(SND_CORRECT); break;                         // correct move
    case 'W': PLAY(SND_WRONG); break;                           // wrong move
    case 'G': PLAY(SND_GHOST); break;                           // ghost appears
    case 'g': break;                                            // ghost passed
    case 'K': PLAY(SND_CAUGHT); break;                          // caught by ghost
    case 'N': PLAY(SND_NEAR); break;                            // ghost is close
    case 'V': heartbeatBpm = 0; PLAY(SND_WIN); break;           // you win
    case 'X': heartbeatBpm = 0; PLAY(SND_LOSE); break;          // game over
    case 'O': heartbeatBpm = 0; stopSound(); setLed(false, false); break; // game closed: all off
    case 'B': heartbeatBpm = constrain(atoi(cmd + 1), 0, 220); break;     // heartbeat speed
    case '?': sendLine("HELLO"); break;
  }
}

// Collects letters until Enter, then runs the command
char xbeeBuf[12], usbBuf[12];
int xbeeLen = 0, usbLen = 0;

void readCommands(Stream &in, char *buf, int &len) {
  while (in.available()) {
    char c = in.read();
    if (c == '\n' || c == '\r') {
      if (len > 0) { buf[len] = '\0'; handleCommand(buf); len = 0; }
    } else if (len < 11) {
      buf[len++] = c;
    } else {
      len = 0;                                // too long = noise, throw away
    }
  }
}

// ---------------- SETUP / LOOP ----------------
void setup() {
  Serial.begin(9600);
  xbee.begin(9600);

  for (int i = 0; i < 4; i++) pinMode(TOUCH_PINS[i], INPUT);
  pinMode(PIN_IR, INPUT);
  pinMode(PIN_RED, OUTPUT);
  pinMode(PIN_GREEN, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);

  setLed(false, true);                        // helper starts green
  tone(PIN_BUZZER, 1000, 120);                // start-up beep

  Serial.println("Scary Heartbeat Maze controller ready");
  sendLine("HELLO");
}

void loop() {
  unsigned long now = millis();
  readTouch(now);
  readIR(now);
  readCommands(xbee, xbeeBuf, xbeeLen);
  readCommands(Serial, usbBuf, usbLen);
  updateBuzzer(now);
}
