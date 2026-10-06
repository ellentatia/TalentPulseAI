# TalentPulseAI

TalentPulseAI is a recruiter decision-support prototype for securely ingesting
resume PDFs and finding candidates relevant to a job description. Semantic
similarity is a discovery aid, not an automated hiring decision.

## Local development

See the [local development guide](docs/local-development.md) for prerequisites,
environment setup, Keycloak recruiter login, running the backend and frontend,
and trying the synthetic-data workflow.

## Project layout

- `TalentPulseAI/` — Spring Boot API, batch resume ingestion, PostgreSQL/PGvector
  search, and Ollama embeddings.
- `frontend/` — Angular recruiter workspace and Keycloak integration.
- `docker/` — local PostgreSQL and Keycloak services and the development realm
  import.
- `tools/verify.go` — local test/build pipeline and separate backend/frontend
  run modes.

## Checks

From the repository root, run the full test and build pipeline:

```powershell
go run .\tools\verify.go
```

This starts the Docker Compose services, verifies the backend, runs frontend
tests, and builds the frontend. It requires Go, Docker Desktop, Java 25, and
Node.js/npm.
