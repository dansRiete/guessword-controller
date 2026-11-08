# GuessWord Controller — Developer Notes

## Infrastructure

- **Server**: `192.168.0.201` (k3s Kubernetes cluster, namespace `guessword`)
- **REST API**: `http://192.168.0.201:24868/api` (context path `/api`, port `24868`)
- **Health endpoint**: `http://192.168.0.201:24868/api/actuator/health`
- **DB**: PostgreSQL at `192.168.0.201:24869`, database `guessword`, user `guessword`

## Release Process

**Pipeline**: push to `master` → SSH to `192.168.0.201` and run `./redeploy.sh` in `~/guessword-controller` → Docker build (runs `./gradlew build`) → push to local registry `localhost:5000/guessword-controller` → `kubectl set image` → rollout wait.

ArgoCD also watches `master` and syncs k8s manifests automatically (`k8s/` directory, excludes `argocd-app.yaml`).

### Deployment steps

1. Run `./gradlew test` — must be green
2. Commit and push to `master`
3. Deploy via `redeploy.sh` on `192.168.0.201`:
   ```bash
   ssh 192.168.0.201 "cd ~/guessword-controller && git pull && ./redeploy.sh"
   ```
4. `redeploy.sh` builds the image, tags it with `<short-sha>-<timestamp>` and `latest`, pushes to `localhost:5000/guessword-controller`, runs `kubectl set image` in the `guessword` namespace, and waits up to 5 minutes for rollout
5. Pod health is verified via the actuator readiness probe at `/api/actuator/health`

To check deployment status manually:

```bash
ssh 192.168.0.201 "kubectl rollout status deployment/guessword-controller -n guessword"
ssh 192.168.0.201 "kubectl logs -n guessword deployment/guessword-controller --tail=50"
```
