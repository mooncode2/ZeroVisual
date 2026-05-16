#!/usr/bin/env bash
# Lunar Client 1.21.11 (Linux) — fixes from main.log analysis (SIGKILL during ichor boot).
set -euo pipefail

MC="${HOME}/.minecraft"
LUNAR_MODS="${HOME}/.lunarclient/profiles/lunar/1.21/mods/fabric-1.21.11"
LUNAR_LOGS="${HOME}/.lunarclient/profiles/lunar/1.21/logs"

echo "== Lunar / Zero fix script =="
echo

echo "[1/4] Creating missing Minecraft config (migration lock error)..."
mkdir -p "${MC}/config" "${MC}/logs" "${MC}/crash-reports"
touch "${MC}/config/.lunar.migrated.dat" 2>/dev/null || true
echo "    OK: ${MC}/config"

echo
echo "[2/4] Memory check (SIGKILL often = Linux OOM killer during class baking)..."
if command -v free >/dev/null 2>&1; then
  free -h
fi
echo
echo "    In Lunar: Settings -> General -> allocated memory -> set at least 6144 MB."
echo "    If you use WSL/Docker/VM, raise RAM limit for the VM too."
echo "    After a failed launch run: dmesg -T | tail -30 | grep -iE 'killed process|out of memory'"

echo
echo "[3/4] Lunar ships Sodium/Iris (do NOT add breaks in fabric.mod.json — loader will refuse to start)."
if [[ -d "${LUNAR_MODS}" ]]; then
  bad_ph=$(find "${LUNAR_MODS}" -maxdepth 1 -iname "*placeholder*1.21.10*.jar" 2>/dev/null | head -1 || true)
  if [[ -n "${bad_ph}" ]]; then
    echo "    WARN: wrong Placeholder API for 1.21.11: $(basename "${bad_ph}")"
    echo "          Use 1.21.11 build or remove it."
  fi
else
  echo "    Mods folder not found: ${LUNAR_MODS}"
fi

echo
echo "[4/4] Rebuild Zero (render/mouse mixins auto-disabled on Lunar via ZeroMixinPlugin):"
echo "    ./gradlew build"
echo "    Copy build/libs/*.jar -> ${LUNAR_MODS}/"
echo
echo "Recommended minimal mod set for Lunar + Zero:"
echo "    - Zero (your build)"
echo "    - Fabric API (0.140+ for 1.21.11)"
echo "    - FerriteCore, Mod Menu (optional)"
echo "    - NOT: Sodium, Iris (built into Lunar / conflicts with Zero mixins)"
echo
echo "Test order:"
echo "  A) Launch Lunar with Zero DISABLED — if still SIGKILL -> RAM/VM issue."
echo "  B) Enable only Zero + Fabric API."
echo "  C) Add other mods one by one."
echo
if [[ -f "${LUNAR_LOGS}/ichor-boot.log" ]]; then
  echo "Last lines of ichor-boot.log:"
  tail -n 25 "${LUNAR_LOGS}/ichor-boot.log"
else
  echo "No ichor-boot.log yet at ${LUNAR_LOGS}/ichor-boot.log"
fi

echo
echo "Done."
