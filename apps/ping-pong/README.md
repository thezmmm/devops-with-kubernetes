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
