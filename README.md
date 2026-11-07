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
│   └── ...
├── k8s/
│   ├── log-output/
│   └── ...
├── scripts/
│   └── helpers/
└── .github/
```

Each application or exercise is kept in its own folder so that it is easy to test and submit.

## Submission notes

The course expects a clear repository layout and release tags for exercises. A good habit is to:

- keep each app in a separate directory
- add a README for each application
- create release tags such as `1.1`, `1.2`, etc.
- link each exercise from the main README to the relevant folder or release

## Exercise links

- [Exercise 1: Log Output (Java)](https://github.com/thezmmm/devops-with-kubernetes/tree/1.1/apps/log-output)
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
