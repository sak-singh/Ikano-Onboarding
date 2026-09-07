# Ikano Onboarding Platform

A sample customer onboarding flow for Ikano Bank, covering private individuals and businesses
across Sweden, Spain and Poland. All external checks (identity, PEP/sanctions, credit bureau,
company registry) are mocked with deterministic responses.

**Live demo:** https://ikano-onboarding-944733492167.europe-west4.run.app

The brief mentioned extra emphasis on deployment, so the app is deployed and there's a
[Deployment](#deployment) section explaining how it gets there.

---

## What it does

You pick a country and a customer type, and the app renders the right journey for that
combination — six flows in total. Each step is a form with server-side validation. Along the way
it calls mocked integrations, records an audit event for every check, and ends with one of three
decisions: **approved**, **manual review**, or **rejected**.

### Private individual

| Step | What happens |
|---|---|
| Identity | National ID (personnummer / DNI-NIE / PESEL) plus a BankID-style identity mock |
| Contact | Address and contact details, validated per country |
| Consent | Consent, PEP/sanctions declaration, tax residency |
| Affordability | Employment, income, expenses, household size |
| Credit bureau | Credit + affordability mock, produces the decision |
| Review | Masked summary, accept terms, submit |

### Business

Organisation number, company registry lookup (Bolagsverket / Registro Mercantil / KRS-CEIDG
style), authorised representative and signatory rights, beneficial owners with KYC, business
activity and turnover, then KYB + sanctions + business credit before review.

### Mocked integrations

Everything is deterministic, so the same input always produces the same outcome — that's what
makes it testable. The mocks live in `service/integration/`:

| Mock | Outcomes |
|---|---|
| Identity / KYC | `verified`, `document_mismatch`, `expired_id`, `manual_review` |
| PEP / sanctions | `no_hit`, `possible_hit`, `confirmed_hit` |
| Credit / affordability | score, debt flags, disposable income, decision reason |
| Company registry | `active_company`, `dissolved`, `unknown_representative` |
| Bank account | `iban_verified`, `name_mismatch`, `unreachable` |

Worth trying: a healthy income on the affordability step gets approved, while a low income with
high expenses gets referred or rejected.

---

## Tech stack

- Java 21, Spring Boot 3.3, Maven
- Spring Data JPA — H2 in memory by default, PostgreSQL via the `postgres` profile
- Thymeleaf server-rendered pages with small vanilla JS enhancements
- JUnit 5 + MockMvc
- Docker, GitHub Actions, Google Artifact Registry, Cloud Run

---

## Running it locally

You need Java 21 and Maven. Nothing else for the default setup.

### Quickest way — H2 in memory

```bash
mvn spring-boot:run
```

Open http://localhost:8080. The database is in memory, so it resets on restart, which is fine for
clicking through a flow. The H2 console is at http://localhost:8080/h2-console (JDBC URL
`jdbc:h2:mem:onboarding`, user `sa`, blank password). It's disabled in the deployed version.

### With PostgreSQL

If you want the data to survive a restart, start Postgres and use the `postgres` profile:

```bash
docker compose up -d postgres
mvn -Dspring-boot.run.profiles=postgres spring-boot:run
```

Then have a look at what got saved:

```bash
docker exec -it ikano-postgres psql -U onboard -d onboarding_dev
```

```sql
SELECT id, country, customer_type, status, created_at
FROM onboarding_session ORDER BY created_at DESC LIMIT 5;

SELECT step_key, integration_type, outcome, description, timestamp
FROM audit_event ORDER BY timestamp DESC LIMIT 20;
```

`audit_event` is the interesting one — it shows what was checked, when, and with what result,
without storing raw personal identifiers.

### Everything in Docker

The image expects a built jar, so package first:

```bash
mvn clean package
docker compose up --build
```

App on http://localhost:8081, Postgres on 5432.

---

## Tests

```bash
mvn test
```

Two integration test classes drive the private and business flows end to end through MockMvc:
creating a session, posting each step, checking that validation rejects bad input, and asserting
the final decision for the pass / manual-review / fail cases.

---

## How the code is organised

```
com.ikano.onboarding
├── api/            REST controllers (private + business), exception handling
├── config/         Flow definitions — step/field config per country and type
├── domain/         JPA entities and enums (session, audit event, outcomes)
├── dto/            Request/response objects
├── repository/     Spring Data repositories
├── service/
│   ├── integration/   The mocked external clients
│   └── ...            Flow orchestration, decisioning, data masking
└── web/            Thymeleaf controllers, request-ID filter
```

The part I'd point at in a review is `config/PrivateFlowConfig` and `config/BusinessFlowConfig`.
The flows are data, not branching logic: a `FlowDefinition` holds a list of `StepDefinition`s,
each with its fields, validation rules and the integration it triggers, and the service layer
just walks that definition. Adding a seventh country means adding a config entry, not editing an
if/else chain.

`SensitiveDataMasker` keeps personal identity numbers out of the review screen and the audit
trail, and `RequestIdFilter` attaches a request ID to every call so a support person can trace a
single application through the logs.

---

## Deployment

Push to `master` → tested, containerised, deployed to Cloud Run. One workflow,
`.github/workflows/pipeline.yml`, with two jobs.

### Job 1 — Build & test (every branch, every PR)

Checks out, sets up Temurin 21 with a Maven cache, runs `mvn verify`, and uploads two artifacts:
the surefire reports and the packaged jar. The jar is handed to the deploy job rather than
rebuilt, so the exact artifact that was tested is the one that ships.

### Job 2 — Deploy (only on `master`)

1. Downloads the jar from job 1
2. Authenticates to Google Cloud and configures Docker for Artifact Registry
3. Builds the image, tagged with the commit SHA and `latest`
4. **Runs the image on the CI runner** with `PORT=8080` and polls `/actuator/health`
5. Pushes to Artifact Registry
6. Deploys to Cloud Run
7. Smoke-tests the live URL and writes it to the job summary

Step 4 exists because of a real failure. My first deploy died with *"The user-provided container
failed to start and listen on the port defined by PORT=8080"*. The cause was
`application.properties` pinning `server.port=8081` — properties beat YAML in Spring's precedence
order, so it quietly overrode the `${PORT:8080}` in `application.yml` and the container never
listened where Cloud Run expected. The fix was one line, but the lesson was that a container which
can't start should fail in CI, where I get the application logs, rather than in Cloud Run, where
all I get is a probe timeout. So the pipeline now starts the container before pushing it.

Deployment is gated on a `production` GitHub Environment, so required reviewers can be switched on
without touching the workflow. Concurrency is set so pushes to the same branch cancel each other,
except on `master`, where a deploy is never cancelled mid-flight.

### Config it needs

| Type | Name | Purpose |
|---|---|---|
| Secret | `GCP_SA_KEY` | Service account JSON for Artifact Registry + Cloud Run |

Project, region, repository and service name are workflow `env` values. The service account needs
`roles/run.admin`, `roles/artifactregistry.writer` and `roles/iam.serviceAccountUser`.

### Operating it

```bash
# live logs
gcloud run services logs tail ikano-onboarding --region europe-west4

# what's deployed
gcloud run revisions list --service ikano-onboarding --region europe-west4

# roll back
gcloud run services update-traffic ikano-onboarding \
  --region europe-west4 --to-revisions=PREVIOUS_REVISION=100
```

Every image is tagged with its commit SHA, so a rollback is just picking an earlier revision.
Cloud Run does the rolling update and only shifts traffic once the new revision passes its health
check. `min-instances=0` keeps the demo free, at the cost of a cold start on the first request
after idle.

---

## Assumptions and tradeoffs

Being explicit about what I chose *not* to do, since the brief asked for that:

**The deployed demo uses in-memory H2.** Data disappears when the instance scales to zero. I did
this to keep the demo self-contained and free to run. The `postgres` profile is already in the
repo and the application code doesn't change — only configuration. For anything real it would be
Cloud SQL with credentials in Secret Manager.

**Schema is generated by Hibernate (`ddl-auto: update`).** Fine for a sample, wrong for
production. The real answer is Flyway with versioned migrations and `ddl-auto: validate`.

**No authentication.** The brief said not to build one. Sessions are addressed by an opaque UUID,
which is enough for a demo but isn't access control — anyone with the ID can read the session. A
real version needs a signed, expiring resume token.

**Country rules are plausible, not legally accurate.** The personnummer, DNI/NIE and PESEL checks
are realistic in shape, but I haven't implemented full checksum validation for all three, and the
risk thresholds are invented.

**Resumability is partial.** State is saved after every step and a session can be reloaded by ID,
so the data model supports it. What's missing is the magic-link token with expiry and distinct
handling of abandoned vs expired applications.

**The business flow is thinner than the private one.** Both work end to end, but I spent more of
the timebox on the private journey and on deployment, given the emphasis in the brief.

---

## What I'd do next

Roughly in priority order:

1. Cloud SQL + Secret Manager, and Flyway for the schema
2. Resume tokens with expiry, plus proper handling of abandoned and expired applications
3. Skip re-running expensive checks when the inputs haven't changed — the audit trail already
   holds enough information to work that out
4. Structured JSON logging with the request ID, and an alert on the manual-review rate
5. Timeouts, retries and a circuit breaker on the integration clients — the mocks have an
   `unreachable` case that currently isn't exercised as hard as it should be
6. More depth on the decisioning tests, ideally parameterised across all six flows

