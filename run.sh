#!/bin/bash

# Lucene Demo - Phase 1 Runner
set -e

echo "=== Lucene Demo Phase 1: Speed Comparison ==="
echo "Building and running..."

# Build the project
mvn clean compile

# Run the main class with arguments
mvn exec:java -Dexec.mainClass="com.example.lucenedemo.cli.Main" -Dexec.args="$*" -q