#!/usr/bin/env bash
# ==============================================================================
# JitForge - Enterprise Runtime Bytecode & JIT Compilation Engine
# Developer: Vivek Umrao
# ==============================================================================
set -e

POD_NAME="jitforge-pod"
VOLUME_NAME="jitforge-classes-vol"
PG_VOLUME="jitforge-pg-vol"
REDIS_VOLUME="jitforge-redis-vol"

echo "=== [1/4] Ensuring Podman volumes exist and configuring permissions ==="
podman volume create $VOLUME_NAME 2>/dev/null || true
podman volume create $PG_VOLUME 2>/dev/null || true
podman volume create $REDIS_VOLUME 2>/dev/null || true

# Ensure non-root compiler user (UID 10001) owns the classes storage volume
podman run --rm -v $VOLUME_NAME:/data docker.io/library/eclipse-temurin:21-jdk-jammy chown -R 10001:10001 /data 2>/dev/null || true

echo "=== [2/4] Ensuring Podman pod exists: $POD_NAME ==="
if ! podman pod exists $POD_NAME 2>/dev/null; then
    podman pod create --name $POD_NAME \
      -p 8080:8080 \
      -p 6379:6379 \
      -p 5432:5432
    echo "Pod $POD_NAME created."
else
    echo "Pod $POD_NAME already exists."
fi

echo "=== [3/4] Starting PostgreSQL & Redis storage tier containers ==="
if ! podman container exists jitforge-postgres 2>/dev/null; then
    podman run -d --pod $POD_NAME \
      --name jitforge-postgres \
      --restart unless-stopped \
      -e POSTGRES_DB=bytecode_db \
      -e POSTGRES_USER=compiler_user \
      -e POSTGRES_PASSWORD=compiler_pass \
      -v $PG_VOLUME:/var/lib/postgresql/data \
      docker.io/library/postgres:15
    echo "jitforge-postgres container started."
else
    podman start jitforge-postgres 2>/dev/null || true
fi

if ! podman container exists jitforge-redis 2>/dev/null; then
    podman run -d --pod $POD_NAME \
      --name jitforge-redis \
      --restart unless-stopped \
      -v $REDIS_VOLUME:/data \
      docker.io/library/redis:7.2-alpine
    echo "jitforge-redis container started."
else
    podman start jitforge-redis 2>/dev/null || true
fi

echo "=== Waiting for storage backends to initialize ==="
sleep 3

echo "=== [4/4] Starting JitForge REST microservice ==="
if ! podman container exists jitforge-service 2>/dev/null; then
    podman run -d --pod $POD_NAME \
      --name jitforge-service \
      --restart unless-stopped \
      -e SERVER_PORT=8080 \
      -e COMPILER_SERVICE=true \
      -e SECURITY_SANDBOX_ENABLED=true \
      -e STORAGE_DIR=/var/data/compiler/classes \
      -e REDIS_HOST=localhost \
      -e REDIS_PORT=6379 \
      -e DB_URL=jdbc:postgresql://localhost:5432/bytecode_db \
      -e DB_USER=compiler_user \
      -e DB_PASSWORD=compiler_pass \
      -v $VOLUME_NAME:/var/data/compiler/classes \
      localhost/jitforge:latest
    echo "jitforge-service container started."
else
    podman start jitforge-service 2>/dev/null || true
fi

echo ""
echo "================================================================================"
echo "JitForge service successfully launched in Podman pod: $POD_NAME"
echo "  - Service URL: http://localhost:8080"
echo "  - Health Check: curl http://localhost:8080/api/health"
echo "  - Tiers Status: curl http://localhost:8080/api/tiers"
echo "================================================================================"
