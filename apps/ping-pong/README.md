# Ping Pong

This application is a tiny Java HTTP server for the DevOps with Kubernetes course. It keeps an in-memory counter and replies with `pong N`, where `N` is the number of requests that have been handled so far.

## Run locally

```bash
docker build -t ping-pong:latest .
docker run --rm -e PORT=8080 -p 8080:8080 ping-pong:latest
```

Then call:

```bash
curl http://localhost:8080/pingpong
```

Example output:

```text
pong 0
pong 1
pong 2
```

## Kubernetes

```bash
kubectl apply -f .\apps\ping-pong\manifests\deployment.yaml
kubectl apply -f .\apps\ping-pong\manifests\service.yaml
kubectl apply -f .\apps\ping-pong\manifests\postgres.yaml
kubectl get pods
kubectl get service ping-pong -w
kubectl logs deployment/ping-pong
```

In GKE, the `ping-pong` service is exposed through a `LoadBalancer`, which provisions an external IP for the app. After the external IP is assigned, you can curl the service directly:

```bash
curl http://<EXTERNAL_IP>/pingpong
```

## Canary validation with Argo Rollouts

The `canary.yaml` manifest changes the ping-pong deployment into a Rollout and adds a Prometheus-based `AnalysisTemplate` to guard the update.

- The template watches the sum of namespace CPU usage over a 5 minute window.
- The threshold is set to `0.8` CPU cores, which is intentionally high enough to avoid blocking normal small updates but low enough to catch a risky spike.
- If the namespace exceeds this value during the analysis window, the rollout is aborted and the update is reverted.

```bash
kubectl apply -f .\apps\ping-pong\manifests\canary.yaml
kubectl argo rollouts get rollout ping-pong -n exercises
```
