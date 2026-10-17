#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .test_classes
javac -d .test_classes app/src/main/java/com/cushionlab/autoguide/Trajectory.java app/src/main/java/com/cushionlab/autoguide/FrameAnalyzer.java tests/RealScreenshotTest.java
java -cp .test_classes RealScreenshotTest tests/fixtures/8ballpool_s26_screen.jpg
