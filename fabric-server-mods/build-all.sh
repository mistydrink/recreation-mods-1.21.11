#!/usr/bin/env sh
# Builds every mod and collects the jars in ./jars
set -e
cd "$(dirname "$0")"
mkdir -p jars
for mod in deathkick nickskins smptools; do
  echo "== Building $mod"
  (cd "$mod" && sh ./gradlew build)
  cp "$mod"/build/libs/*.jar jars/
done
echo "Done. Jars are in $(pwd)/jars"
