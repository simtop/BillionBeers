#!/bin/sh
set -eu

task=:ios-shared:embedAndSignAppleFrameworkForXcode
case "${IOS_HOST_WRITE_VERIFICATION_METADATA:-0}" in
  0) exec ./gradlew "$task" ;;
  1)
    exec ./gradlew \
      --write-verification-metadata sha256 \
      --no-configuration-cache \
      --continue \
      '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8' \
      "$task"
    ;;
  *)
    echo "IOS_HOST_WRITE_VERIFICATION_METADATA must be 0 or 1" >&2
    exit 2
    ;;
esac
