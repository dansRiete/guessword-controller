#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

IMAGE_REPO="localhost:5000/guessword-controller"
TAG="$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M%S)"

echo "=== 1. Building & testing inside Docker (${IMAGE_REPO}:${TAG}) ==="
docker build --progress=plain -t "${IMAGE_REPO}:${TAG}" -t "${IMAGE_REPO}:latest" .

echo "=== 2. Pushing image to local registry (${IMAGE_REPO}) ==="
docker push "${IMAGE_REPO}:${TAG}"
docker push "${IMAGE_REPO}:latest"

echo "=== 3. Rolling out deployment in Kubernetes (namespace: guessword) ==="
kubectl set image deployment/guessword-controller "guessword-controller=${IMAGE_REPO}:${TAG}" -n guessword
kubectl rollout status deployment/guessword-controller -n guessword --timeout=300s

echo "=== Redeploy complete: ${IMAGE_REPO}:${TAG} ==="
