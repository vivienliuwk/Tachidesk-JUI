#!/bin/bash

rpm="$(find ./ -iname '*.rpm')"
dir="$(dirname "$rpm")"

echo "Adding java and libc++ dependency to rpm file $rpm in $dir"

rpmrebuild -d "$dir" -np --change-spec-requires="echo Requires: java-1.8.0-openjdk-headless,libcxx" "$rpm"

echo "Cleaning up"
rm -f "$rpm"
mv "$dir"/*/*'.rpm' "$dir"