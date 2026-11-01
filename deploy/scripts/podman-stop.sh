#!/usr/bin/env bash
# ==============================================================================
# JitForge - Enterprise Runtime Bytecode & JIT Compilation Engine
# Developer: Vivek Umrao
# ==============================================================================

POD_NAME="jitforge-pod"

echo "Stopping containers in pod: $POD_NAME..."
podman stop jitforge-service jitforge-postgres jitforge-redis 2>/dev/null || true

echo "Removing containers..."
podman rm jitforge-service jitforge-postgres jitforge-redis 2>/dev/null || true

echo "Removing pod: $POD_NAME..."
podman pod rm $POD_NAME 2>/dev/null || true

echo "JitForge pod and containers stopped."
