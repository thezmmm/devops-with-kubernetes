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

## Local tools

Recommended tools for this course:

- Docker
- Docker Compose
- kubectl
- Kubernetes cluster tooling such as kind, k3d, or minikube
- Git
- VS Code or another editor
- Optional: Helm, k9s, kubens, or similar helpers

## Repository structure

```text
devops-with-kubernetes/
├── README.md
├── .gitignore
├── docs/
│   └── notes/
├── apps/
│   ├── log-output/
│   └── ...
├── k8s/
│   ├── log-output/
│   └── ...
├── scripts/
│   └── helpers/
└── .github/
```

Each application or exercise is kept in its own folder so that it is easy to test and submit.

## Typical workflow

1. Read the course chapter and exercise instructions.
2. Build and test the app locally with Docker.
3. Run the app in a local Kubernetes cluster.
4. Validate the deployment, logs, and behavior.
5. Document the result and commit the changes.
6. Create a GitHub release tag when required by the exercise.

## Submission notes

The course expects a clear repository layout and release tags for exercises. A good habit is to:

- keep each app in a separate directory
- add a README for each application
- create release tags such as `1.1`, `1.2`, etc.
- link each exercise from the main README to the relevant folder or release

## Exercise links

- [Exercise 1: Log Output (Java)](apps/log-output/README.md)
- Release tag: `1.1`

## Learning log

- [x] Chapter 1: Getting started
- [ ] Chapter 2: Kubernetes basics
- [ ] Chapter 3: Kubernetes applications
- [ ] Chapter 4: Kubernetes in the cloud
- [ ] Chapter 5: Continuous delivery
- [ ] Chapter 6: Final project

## Notes

- Always validate Kubernetes manifests before applying them.
- Prefer small, focused apps for learning new Kubernetes concepts.
- Keep environment variables and secrets out of version control.
- Clean up cloud resources when experiments are complete.

## License

This repository is for learning and course practice. Follow the course instructions and relevant licensing rules for any shared examples or code.

## Contributing

This is primarily a personal learning repository, but relevant improvements and notes are welcome.
