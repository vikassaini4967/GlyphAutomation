#!/usr/bin/env bash
# One-click run: headless Glyph Mailinator sanity test
set -e
mvn -q clean compile exec:java
