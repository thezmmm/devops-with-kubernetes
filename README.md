# DevOps with Kubernetes

This repository is used for learning and practicing the DevOps with Kubernetes course material from the University of Helsinki / MOOC.fi.

## Course information

- Course: DevOps with Kubernetes
- Platform: MOOC.fi / University of Helsinki
- Material: https://courses.mooc.fi/org/uh-cs/courses/devops-with-kubernetes-2026

This repo will hold notes, application source code, Kubernetes manifests, deployment scripts, and exercise solutions as I progress through the course.

## Repository purpose

The main goals of this repository are:

- learn containerization with Docker
- build small web applications and APIs
- deploy applications to Kubernetes
- manage workloads, services, ingress, storage, and scaling
- practice CI/CD and automation workflows
- document learnings and exercise submissions

## Recommended local tools

Before starting the exercises, make sure you have the following installed:

- Docker
- Docker Compose
- kubectl
- Kubernetes cluster tooling such as kind or minikube
- Git
- A code editor such as VS Code
- Optional: Helm, kubens, k9s, or other Kubernetes helpers

## Suggested repository layout

A practical structure for this course looks like this:

```text
.devops-with-kubernetes/
├── README.md
├── .gitignore
├── docs/
│   └── notes/
├── apps/
│   ├── app-1/
│   ├── app-2/
│   └── ...
├── k8s/
│   ├── manifests/
│   ├── configs/
│   └── secrets/
├── scripts/
│   └── helper-scripts/
└── exercises/
    └── exercise-logs/
```

Use one directory per application or per exercise to keep the repository organized.

## Typical workflow

1. Read the course chapter and exercise instructions.
2. Build the application locally with Docker.
3. Run it in a local Kubernetes cluster.
4. Validate configuration, networking, and resource behavior.
5. Commit and document the result.
6. Tag releases when required by the course submission format.

## Submission notes

The course material expects repositories to be organized clearly and often asks students to create a GitHub release for each exercise. A good habit is to:

- keep exercise-related apps or manifests in separate directories
- keep a README in each app directory
- create release tags like `1.1`, `1.2`, etc. when required
- include links from the main repo README to the relevant exercise folders/releases

## Learning log

This section can be updated as exercises are completed.

- [ ] Chapter 1: Getting started
- [ ] Chapter 2: Kubernetes basics
- [ ] Chapter 3: Kubernetes applications
- [ ] Chapter 4: Kubernetes in the cloud
- [ ] Chapter 5: Continuous delivery
- [ ] Chapter 6: Final project

## Notes

- Always verify Kubernetes manifests before applying them to a cluster.
- Prefer small, focused apps when practicing new Kubernetes features.
- Keep environment variables and secrets out of version control.
- When working with cloud providers, use dedicated test projects and clean up resources when done.

## License

This repository is for learning purposes and follows the course material and university guidance. Individual source code and examples should be treated according to the course's licensing and submission rules.

## Contributing

This is primarily a personal learning repository, but improvements and notes are welcome if they are relevant to the course material.
