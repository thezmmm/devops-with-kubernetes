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

The deployment file sets the same environment variable and runs the application in a pod:

```bash
kubectl apply -f ../../k8s/todo-server/deployment.yaml
kubectl get pods
kubectl logs deployment/todo-server
```
