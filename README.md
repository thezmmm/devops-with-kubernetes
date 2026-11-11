# DevOps with Kubernetes

This repository is used for learning and practicing the DevOps with Kubernetes course from the University of Helsinki / MOOC.fi.

## Course

- Course: DevOps with Kubernetes
- Platform: MOOC.fi / University of Helsinki
- Course material: https://courses.mooc.fi/org/uh-cs/courses/devops-with-kubernetes-2026

The goal of this repo is to keep notes, application code, Kubernetes manifests, and exercise submissions organized while progressing through the course.

## Repository purpose

This repository is used to:

- learn Docker and container basics
- build small applications and APIs
- deploy workloads to Kubernetes
- practice networking, scaling, and storage topics
- document exercise results and learning notes
- submit GitHub release links for course exercises

## Repository structure

```text
devops-with-kubernetes/
├── README.md
├── .gitignore
├── docs/
│   └── notes/
├── apps/
│   ├── log-output/
│   │   ├── manifests/
│   │   └── ...
│   ├── ping-pong/
│   │   ├── manifests/
│   │   └── ...
│   ├── todo-server/
│   │   ├── manifests/
│   │   └── ...
│   └── ...
├── persistent/
│   ├── pv.yaml
│   └── pvc.yaml
├── scripts/
│   └── helpers/
└── .github/
```

Each application keeps its own source code and deployment manifests together so that the project is easier to track and submit.

## Submission notes

The course expects a clear repository layout and release tags for exercises. A good habit is to:

- keep each app in a separate directory
- add a README for each application
- create release tags such as `1.1`, `1.2`, etc.
- link each exercise from the main README to the relevant folder or release

## Exercise links

- [Exercise 1: Log Output (Java)](https://github.com/thezmmm/devops-with-kubernetes/tree/1.1/apps/log-output)
- Release tag: `1.1`
- [Exercise 1.2: Todo Server (Java)](https://github.com/thezmmm/devops-with-kubernetes/tree/1.2/apps/todo-server)
- Release tag: `1.2`
- [Exercise 1.3: Declarative Log Output Manifest](https://github.com/thezmmm/devops-with-kubernetes/tree/1.3/apps/log-output/manifests)
- Release tag: `1.3`
- [Exercise 1.4: Todo Server Deployment](https://github.com/thezmmm/devops-with-kubernetes/tree/1.4/apps/todo-server)
- Release tag: `1.4`
- [Exercise 1.5: Todo UI via port-forward](https://github.com/thezmmm/devops-with-kubernetes/tree/1.5/apps/todo-server)
- Release tag: `1.5`
- [Exercise 1.6: Todo UI via NodePort](https://github.com/thezmmm/devops-with-kubernetes/tree/1.6/apps/todo-server)
- Release tag: `1.6`
- [Exercise 1.7: Log Output status endpoint and ingress](https://github.com/thezmmm/devops-with-kubernetes/tree/1.7/apps/log-output)
- Release tag: `1.7`
- [Exercise 1.8: Todo app access via ingress](https://github.com/thezmmm/devops-with-kubernetes/tree/1.8/apps/todo-server)
- Release tag: `1.8`
- [Exercise 1.9: Ping-pong app via shared ingress](https://github.com/thezmmm/devops-with-kubernetes/tree/1.9/apps/ping-pong)
- Release tag: `1.9`
- [Exercise 1.10: Multi-container log-output pod](https://github.com/thezmmm/devops-with-kubernetes/tree/1.10/apps/log-output)
- Release tag: `1.10`
- [Exercise 1.11: Shared persistent volume between ping-pong and log-output](https://github.com/thezmmm/devops-with-kubernetes/tree/1.11/apps)
- Release tag: `1.11`
- [Exercise 1.12: Cached random image in todo-server](https://github.com/thezmmm/devops-with-kubernetes/tree/1.12/apps/todo-server)
- Release tag: `1.12`
- [Exercise 1.13: Todo input form with validation and in-page updates](https://github.com/thezmmm/devops-with-kubernetes/tree/1.13/apps/todo-server)
- Release tag: `1.13`
- [Exercise 2.1: Connect log-output and ping-pong with HTTP](https://github.com/thezmmm/devops-with-kubernetes/tree/2.1/apps)
- Release tag: `2.1`
- [Exercise 2.2: Move todo storage into a dedicated backend service](https://github.com/thezmmm/devops-with-kubernetes/tree/2.2/apps)
- Release tag: `2.2`
- [Exercise 2.3: Create the exercises namespace for course apps](https://github.com/thezmmm/devops-with-kubernetes/tree/2.3/namespaces)
- Release tag: `2.3`
- [Exercise 2.4: Create the project namespace for project apps](https://github.com/thezmmm/devops-with-kubernetes/tree/2.4/namespaces)
- Release tag: `2.4`
- [Exercise 2.5: Add a ConfigMap-backed MESSAGE and file to log-output](https://github.com/thezmmm/devops-with-kubernetes/tree/2.5/apps/log-output)
- Release tag: `2.5`
- [Exercise 2.6: Remove hardcoded runtime configuration and use env-based settings](https://github.com/thezmmm/devops-with-kubernetes/tree/2.6/apps)
- Release tag: `2.6`
- [Exercise 2.7: Store the ping-pong counter in PostgreSQL with a StatefulSet](https://github.com/thezmmm/devops-with-kubernetes/tree/2.7/apps/ping-pong)
- Release tag: `2.7`
- [Exercise 2.8: Store todos in PostgreSQL and use Secrets/ConfigMaps](https://github.com/thezmmm/devops-with-kubernetes/tree/2.8/apps/todo-backend)
- Release tag: `2.8`
- [Exercise 2.9: Create a CronJob that inserts random Wikipedia reading todos](https://github.com/thezmmm/devops-with-kubernetes/tree/2.9/apps/todo-backend)
- Release tag: `2.9`
- [Exercise 3.1: Deploy Ping-pong to GKE with a LoadBalancer service](https://github.com/thezmmm/devops-with-kubernetes/tree/3.1/apps/ping-pong)
- Release tag: `3.1`
- [Exercise 3.2: Deploy log-output and ping-pong to GKE via Ingress](https://github.com/thezmmm/devops-with-kubernetes/tree/3.2/apps)
- Release tag: `3.2`
- [Exercise 3.3: Replace the Ingress with Gateway API for log-output and ping-pong](https://github.com/thezmmm/devops-with-kubernetes/tree/3.3/apps)
- Release tag: `3.3`
- [Exercise 3.4: Rewritten routing for ping-pong behind Gateway API](https://github.com/thezmmm/devops-with-kubernetes/tree/3.4/apps)
- Release tag: `3.4`
- [Exercise 4.1: Add readiness probes to ping-pong and log-output](https://github.com/thezmmm/devops-with-kubernetes/tree/4.1/apps)
- Release tag: `4.1`
- [Exercise 4.2: Health checks and break app button for the todo project](https://github.com/thezmmm/devops-with-kubernetes/tree/4.2/apps)
- Release tag: `4.2`
- [Exercise 4.3: Rollout safety with Argo Rollouts canary analysis](https://github.com/thezmmm/devops-with-kubernetes/tree/4.3/apps)
- Release tag: `4.3`
- [Exercise 4.4: Canary rollback guard for ping-pong](https://github.com/thezmmm/devops-with-kubernetes/tree/4.4/apps)
- Release tag: `4.4`
- [Exercise 4.5: Todo mark-done feature and styling](https://github.com/thezmmm/devops-with-kubernetes/tree/4.5/apps)
- Release tag: `4.5`
- [Exercise 4.6: NATS broadcaster for todo events](https://github.com/thezmmm/devops-with-kubernetes/tree/4.6/apps)
- Release tag: `4.6`
- [Exercise 4.7: GitOps deployment for log-output](https://github.com/thezmmm/devops-with-kubernetes/tree/4.7/apps)
- Release tag: `4.7`
- [Exercise 4.8: GitOps deployment for the project](https://github.com/thezmmm/devops-with-kubernetes/tree/4.8/apps)
- Release tag: `4.8`
- [Exercise 4.9: Split project GitOps into staging and production](https://github.com/thezmmm/devops-with-kubernetes/tree/4.9/apps)
- Release tag: `4.9`
- [Exercise 4.10: Separate code and config repositories for the project](https://github.com/thezmmm/devops-with-kubernetes/tree/4.10/apps)
- Release tag: `4.10`

## Notes

- The project uses separate code/config repositories for the final GitOps setup.
- The shared PVC for the project is created in the `project` namespace so the `todo-server` Deployment can mount `shared-data` correctly.
- Exercise 4.1 includes readiness behavior for ping-pong and log-output, and 4.2 adds the break/recover app health flow.
- Exercise 4.3/4.4/4.5 were finalized on the same canary rollout revision to satisfy the course tag alignment.
- Always validate Kubernetes manifests before applying them.
- Prefer small, focused apps for learning new Kubernetes concepts.
- Keep environment variables and secrets out of version control.
- Clean up cloud resources when experiments are complete.

## License

This repository is for learning and course practice. Follow the course instructions and relevant licensing rules for any shared examples or code.