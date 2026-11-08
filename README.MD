# GuessWord Controller

Spring Boot 3.0.1 backend for the GuessWord vocabulary learning app.

## Network / Infrastructure

| Resource | Address |
|---|---|
| Server (i7-4770k, k8s) | `192.168.0.201` (SSH to this server for `kubectl` and host file access) |
| Controller REST API | `http://192.168.0.201:24868/api` |
| Health Check | `http://192.168.0.201:24868/api/actuator/health` |
| PostgreSQL | `192.168.0.201:24869`, db `guessword` |
| Local Docker Registry | `localhost:5000` (on `192.168.0.201`) |

## Build & Test

```bash
./gradlew test
```

## Deployment

**Pipeline**: push to `master` → SSH to `192.168.0.201` and run `./redeploy.sh` in `~/guessword-controller` → Docker build (runs Gradle build & tests) → push to local registry `localhost:5000/guessword-controller` → `kubectl set image` in `guessword` namespace → rollout wait.

ArgoCD also watches `master` and syncs k8s manifests automatically (`k8s/` directory, excludes `argocd-app.yaml`).

```bash
ssh 192.168.0.201 "cd ~/guessword-controller && git pull && ./redeploy.sh"
```

### Check deployment status

```bash
ssh 192.168.0.201 "kubectl rollout status deployment/guessword-controller -n guessword"
ssh 192.168.0.201 "kubectl logs -n guessword deployment/guessword-controller --tail=50"
```
