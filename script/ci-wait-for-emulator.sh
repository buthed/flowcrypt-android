#!/bin/bash

# Originally written by Ralf Kistner <ralf@embarkmobile.com>, but placed in the public domain

set +e

bootanim=""
failcounter=0
timeout_in_sec=90

until [[ "$bootanim" =~ "stopped" ]]; do
  bootanim=`adb -e shell getprop init.svc.bootanim 2>&1 &`
  if [[ "$bootanim" =~ "device not found" || "$bootanim" =~ "device offline" || "$bootanim" =~ "running" ]]; then
    echo "Waiting for emulator to start"
    if [[ $failcounter -gt timeout_in_sec ]]; then
      echo "Timeout ($timeout_in_sec seconds) reached; failed to start emulator"
      exit 1
    fi
  fi
  let "failcounter += 1"
  sleep 1
done

echo "Emulator is ready"