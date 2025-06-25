#!/bin/bash

echo "Building SSH Server for Raspberry Pi..."

# Clean previous builds
./gradlew clean

# Build the server-only JAR (without JavaFX)
./gradlew serverPiJar

echo "Build complete!"
echo "Server JAR location: app/build/libs/ssh-server-pi-all.jar"
echo ""
echo "To deploy to Raspberry Pi:"
echo "1. Copy the JAR file to your Raspberry Pi"
echo "2. Copy the data/ directory to your Raspberry Pi"
echo "3. Run: java -jar ssh-server-pi-all.jar"
echo ""
echo "Make sure your Raspberry Pi has Java 21+ installed!" 