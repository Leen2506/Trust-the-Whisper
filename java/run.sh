#!/bin/sh
# Compile and run Scary Heartbeat Maze (macOS / Linux). Run from the java folder.
cd "$(dirname "$0")" || exit 1
rm -rf out
javac -d out -cp lib/jSerialComm-2.11.0.jar src/shm/*.java || exit 1
java -cp "out:lib/jSerialComm-2.11.0.jar" shm.Main "$@"
