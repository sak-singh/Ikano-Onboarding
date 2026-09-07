# AWS deployment guide (DevOps focus)

- Explain rollback strategy (re-deploy previous task definition)
- Show DB records in RDS for submitted onboarding session
- Show CloudWatch logs for startup and health
- Show ECS service with latest task revision
- Show a pipeline run (tests + image + deploy)

## Demo points for interview

5. ECS service performs rolling update and waits for stability.
4. ECS task definition rendered with new image.
3. Docker image built and pushed to ECR (`<sha>` tag).
2. Workflow runs `mvn clean test package`.
1. Push to `main` or `master`.

## Online deployment flow

```
# app should be available on http://localhost:8081

docker compose up --build

# run local stack
```bash

## Local deployment rehearsal

- CloudWatch logs (if required by deployment action)
- ECS service deployment
- ECS task definition update
- ECR push

Configure an IAM role with trust policy for GitHub OIDC (`token.actions.githubusercontent.com`) and permissions for:

## IAM OIDC notes

- `AWS_ROLE_TO_ASSUME` (IAM role ARN trusted for GitHub OIDC)

### Repo secrets

- `ECS_SERVICE`
- `ECS_CLUSTER`
- `ECR_REPOSITORY`
- `AWS_REGION` (for example `eu-north-1`)

### Repo variables

## Required GitHub setup

- No long-lived AWS access keys in GitHub (OIDC role assumption)
- Rolling deployment with health checks and service stability wait
- Deterministic build artifact (image tagged by commit SHA)
- Reproducible pipeline from source to online environment
- Strong deployment emphasis (requested in email)

## Why this matches the assignment

- CloudWatch Logs / metrics / alarms
- AWS Secrets Manager (DB credentials)
- Amazon RDS PostgreSQL (data)
- Amazon ECS Fargate + ALB (online app)
- Amazon ECR (container images)
- GitHub Actions (`ci.yml`, `deploy-aws.yml`)
- GitHub (private repo)

## Architecture

This project includes a GitHub Actions CI/CD pipeline that builds/tests the app, builds a Docker image, pushes to Amazon ECR and deploys to Amazon ECS Fargate.

