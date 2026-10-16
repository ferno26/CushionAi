#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .test_classes
javac -d .test_classes app/src/main/java/com/cushionlab/autoguide/Trajectory.java app/src/main/java/com/cushionlab/autoguide/FrameAnalyzer.java tests/CoreSmokeTest.java
if [ -f tests/fixtures/01-8748.jpg ] && [ -f tests/fixtures/02-8750.jpg ]; then
  java -ea -cp .test_classes CoreSmokeTest tests/fixtures/01-8748.jpg tests/fixtures/02-8750.jpg
else
  java -ea -cp .test_classes CoreSmokeTest
fi
