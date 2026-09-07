# Ikano Onboarding Platform

Sample onboarding web app for Ikano take-home assignment with private + business journeys, deterministic mocked integrations, persistence, audit trail, and DevOps-focused deployment.

## Stack

- Java 21, Spring Boot, Maven
- PostgreSQL (primary for deployment), H2 (local fallback)
- Docker / Docker Compose
- GitHub Actions CI/CD
- AWS target deployment: ECR + ECS Fargate

## Local run (Postgres via Docker)

```bash
cd /Users/saketkumar/Kafka_git/kafka-streams-course/ikano-onboarding-platform
docker compose up --build
```

App: `http://localhost:8081`

## Local run (Maven, Postgres profile)

```bash
cd /Users/saketkumar/Kafka_git/kafka-streams-course/ikano-onboarding-platform
export JAVA_HOME=/Users/saketkumar/Library/Java/JavaVirtualMachines/corretto-21.0.12.1/Contents/Home
mvn -Dspring-boot.run.profiles=postgres -Dspring-boot.run.arguments="--server.port=8081" spring-boot:run
```

## Tests

```bash
cd /Users/saketkumar/Kafka_git/kafka-streams-course/ikano-onboarding-platform
mvn test
```

## Deployment (GitHub Actions + AWS)

### Workflows

- `/.github/workflows/ci.yml` - run tests and build artifact on PR/push
- `/.github/workflows/security.yml` - CodeQL, secret scan, dependency scan, and Trivy scan
- `/.github/workflows/deploy-aws.yml` - build image, push to ECR, deploy to ECS

### Required GitHub repository configuration

Repository variables:

- `AWS_REGION`
- `ECR_REPOSITORY`
- `ECS_CLUSTER`
- `ECS_SERVICE`

Repository secrets:

- `AWS_ROLE_TO_ASSUME` (OIDC-enabled IAM role ARN)

### AWS deployment files

- `deploy/aws/ecs-taskdef.json` - ECS task template
- `deploy/aws/apprunner.yaml` - optional App Runner descriptor
- `docs/deployment-aws.md` - step-by-step deployment explanation

## How this addresses the DevOps emphasis

- End-to-end CI/CD from private GitHub repo to online environment
- Immutable Docker image tagged by commit SHA
- Automated rolling deployment on ECS with health checks
- Runtime secrets from AWS Secrets Manager (no hardcoded production secrets)
- CloudWatch logging support for operations and incident investigation
- Security checks in CI: SAST (CodeQL), secret scanning (Gitleaks), dependency vulnerability scan (OWASP), and container/file scan (Trivy)

## Enforcing quality gates in GitHub

Set branch protection on `main` and require status checks before merge:

- `ci / test-build`
- `security / CodeQL (SAST)`
- `security / Secret Scan (Gitleaks)`
- `security / Dependency Check (OWASP)`
- `security / Container/FileSystem Scan (Trivy)`

This ensures unit tests and security checks pass before code is merged.

## Notes and assumptions

- National eID/KYC/registry/credit integrations are mocked (deterministic), as required.
- No production authentication implemented (explicitly out-of-scope in brief).
- Business and private flows are configuration-driven to avoid nested if/else logic.

