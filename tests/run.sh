#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .test_classes
javac -d .test_classes \
 app/src/main/java/com/cushionlab/autoguide/Trajectory.java \
 app/src/main/java/com/cushionlab/autoguide/AimGuides.java \
 app/src/main/java/com/cushionlab/autoguide/FrameAnalyzer.java \
 tests/CoreSmokeTest.java tests/GuideFeatureTest.java
java -ea -cp .test_classes CoreSmokeTest
java -ea -cp .test_classes GuideFeatureTest
