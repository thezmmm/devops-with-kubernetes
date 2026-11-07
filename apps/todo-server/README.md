# Todo Server

This application is a simple Java HTTP server for the DevOps with Kubernetes course.

It starts on the port specified by the `PORT` environment variable and prints:

```text
Server started in port 8080
```

## Run locally

```bash
docker build -t todo-server:latest .
docker run --rm -p 8080:8080 -e PORT=8080 todo-server:latest
```

Then open http://localhost:8080/ to confirm the server responds.

## Kubernetes

The deployment file sets the same environment variable and runs the application in a pod. From the repository root, use:

```bash
kubectl apply -f .\apps\todo-server\manifests\deployment.yaml
kubectl get pods
kubectl logs deployment/todo-server
```

If you run the command from inside `apps\todo-server`, use:

```bash
kubectl apply -f .\manifests\deployment.yaml
```

This Deployment intentionally does not expose a Service yet; port access will be configured in a later exercise.
