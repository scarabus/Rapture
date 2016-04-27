#!/bin/bash

{
  env && ./gradlew test && cat `find / -type f -exec grep -l "Expected 3 rows" {} \;` 
} || {
  cat RaptureCore/build/reports/tests/index.html &&\
  cat RaptureCore/build/reports/tests/classes/rapture.dp.NestedSplitStepTest.html &&\
  cat RaptureCore/build/reports/tests/classes/rapture.dp.SimpleForkStepTest.html &&\
  cat RaptureCore/build/reports/tests/classes/rapture.dp.MemoryIndexHandlerTest.html
}
