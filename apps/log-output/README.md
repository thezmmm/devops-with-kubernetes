# Log Output

This application is split into two containers in a single pod for the multi-container exercise.

- The `logger` container creates a random ID on startup and appends a timestamped line to a shared file every 5 seconds.
- The `reader` container reads the shared file and serves it through HTTP.

## Run locally

```bash
docker build -t log-output:latest .
```

To test the two roles manually, run them in separate terminals:

```bash
docker run --rm -e LOG_FILE_PATH=/tmp/log-output.txt --name log-output-writer log-output:latest java -jar /app/log-output.jar writer
```

```bash
docker run --rm -e PORT=8080 -e LOG_FILE_PATH=/tmp/log-output.txt -p 8080:8080 --name log-output-reader log-output:latest java -jar /app/log-output.jar reader
```

Then open:

```text
http://localhost:8080/
```

## Kubernetes

```bash
kubectl apply -f .\apps\log-output\manifests\deployment.yaml
kubectl apply -f .\apps\log-output\manifests\service.yaml
kubectl apply -f .\apps\log-output\manifests\ingress.yaml
kubectl get gatewayclass
kubectl get gateway
kubectl get httproute
kubectl get pods
kubectl logs deployment/log-output -c logger
kubectl logs deployment/log-output -c reader
```

The app is exposed through a Gateway API `HTTPRoute` instead of a classic Ingress. `/pingpong` is rewritten to `/` before it reaches the `ping-pong` service, so the app itself only needs to serve the root path while the cluster URL stays friendly for clients.
