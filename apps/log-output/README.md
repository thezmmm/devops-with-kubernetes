# Log Output

This application is implemented in Java and generates a random UUID when it starts. It prints the UUID together with a UTC timestamp every 5 seconds and exposes a `/status` endpoint that returns the current timestamp and the same random string.

## Run locally

```bash
docker build -t log-output:latest .
docker run --rm -e PORT=8080 -p 8080:8080 log-output:latest
```

Then open:

```text
http://localhost:8080/status
```

Expected response:

```json
{"timestamp":"2026-11-08T00:00:00.000Z","id":"7f4b0d3e-..."}
```

## Kubernetes

```bash
kubectl apply -f .\apps\log-output\manifests\deployment.yaml
kubectl apply -f .\apps\log-output\manifests\service.yaml
kubectl apply -f .\apps\log-output\manifests\ingress.yaml
kubectl get pods
kubectl logs deployment/log-output
```

If the ingress controller is not installed yet, install it first, then test the app with a Host header or by using a local DNS entry such as `log-output.local`.
