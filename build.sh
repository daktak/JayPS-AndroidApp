#!/bin/sh
export ANDROID_HOME=$HOME/Android/Sdk ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export JAVA_HOME=$HOME/jdk-21.0.12+8 
export PATH=$JAVA_HOME/bin:$BUN_INSTALL/bin:$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$HOME/jdk-21.0.12+8/bin

./gradlew assembleDebug --offline

