# Todo Server

This application is a simple Java HTTP server for the DevOps with Kubernetes course.

It serves the todo web UI and keeps the random image cache logic. The actual todo items are stored in a separate `todo-backend` service, and this frontend app fetches the todo list from that backend before rendering the page.

## Run locally

```bash
docker build -t todo-server:latest .
docker run --rm -p 8080:8080 -e PORT=8080 -e IMAGE_CACHE_DIR=/data/images -v todo-cache:/data todo-server:latest
```

Then open http://localhost:8080/ to confirm the page loads and the image is displayed.

## Kubernetes

```bash
kubectl apply -f .\persistent\pv.yaml
kubectl apply -f .\persistent\pvc.yaml
kubectl apply -f .\apps\todo-server\manifests\deployment.yaml
kubectl apply -f .\apps\todo-server\manifests\ingress.yaml
kubectl get pods
kubectl logs deployment/todo-server
```

The app mounts the shared PVC at `/data` and writes the cached image there so it survives container restarts.

For browser access in k3d, use:

```text
http://localhost:32181/
```

This keeps the hostname simple and uses `localhost` directly instead of a custom host name.
