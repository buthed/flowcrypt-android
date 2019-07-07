#!/bin/bash

set -euxo pipefail

if [ -e "~/Android" ]; then
    echo "~/Android already exists, skipping installation"
    export PATH="$ANDROID_SDK_ROOT/tools:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
else
    echo "~/Android does not exist, installing"
    mkdir -p $ANDROID_SDK_ROOT
    sudo apt-get -q update && sudo apt-get -q install -y libc6-i386 lib32stdc++6 lib32gcc1 lib32ncurses5 lib32z1
    bash -c '! [ -e "~/Android/$SDK_ARCHIVE" ] && wget https://dl.google.com/android/repository/$SDK_ARCHIVE -P ~/Android/'
    unzip -qq ~/Android/$SDK_ARCHIVE -d $ANDROID_SDK_ROOT && rm ~/Android/$SDK_ARCHIVE
    export PATH="$ANDROID_SDK_ROOT/tools:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
    echo "yes" | sdkmanager --licenses > /dev/null
    (while sleep 3; do echo "y"; done) | sdkmanager "build-tools;26.0.1" "platforms;android-24" "extras;google;m2repository" "extras;android;m2repository" "platform-tools" "emulator" "system-images;android-24;google_apis;armeabi-v7a" > /dev/null
    echo -ne '\n' | avdmanager -v create avd -n semaphore-android-dev -k "system-images;android-24;google_apis;armeabi-v7a" --tag "google_apis" --abi "armeabi-v7a"
fi

sdkmanager --list