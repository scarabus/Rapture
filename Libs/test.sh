#!/bin/bash

{
  ./gradlew test ;
  zip -r - `find .. -name reports` | uuencode - 2>/dev/null
} || {
  cat RaptureCore/build/reports/tests/index.html &&\
  cat RaptureCore/build/reports/tests/classes/rapture.dp.NestedSplitStepTest.html &&\
  cat RaptureCore/build/reports/tests/classes/rapture.dp.SimpleForkStepTest.html &&\
  cat RaptureCore/build/reports/tests/classes/rapture.dp.MemoryIndexHandlerTest.html
}
