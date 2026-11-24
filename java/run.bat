@echo off
REM Compile and run Scary Heartbeat Maze (Windows). Run from the java folder.
cd /d %~dp0
if exist out rmdir /s /q out
javac -d out -cp lib\jSerialComm-2.11.0.jar src\shm\*.java || (pause & exit /b 1)
java -cp "out;lib\jSerialComm-2.11.0.jar" shm.Main %*
