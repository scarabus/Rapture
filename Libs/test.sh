#!/bin/bash

{
  ./gradlew test
} || {
  (echo Directory is `pwd`;
  find .. -name reports;
  zip -r - `find .. -name reports` | uuencode - 2>/dev/null;
  echo RaptureCore/build/reports/tests/index.html */build/reports/tests/classes/*html;
  cat RaptureCore/build/reports/tests/index.html */build/reports/tests/classes/*html;
  echo "======";)
}
