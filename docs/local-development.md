# Local development

## Prerequisites

- Docker Desktop
- Go
- Java 25
- Node.js and npm

The Go runner starts the PostgreSQL and Keycloak Compose services, waits for
PostgreSQL, and passes the database password from `docker/.env` to the backend.
It does not print the password.

## Configure local environment

From the repository root, create the Docker environment file from the template:

```powershell
Copy-Item .\docker\.env.example .\docker\.env
```

If `docker/.env` already exists, keep it and do not overwrite it. Replace the
placeholder database and Keycloak admin passwords with local values. Never
commit `docker/.env` or share its credentials.

## Keycloak recruiter login

The local realm export at `docker/keycloak/recruitment-realm.json` configures
the `recruitment-realm`, public `talentpulse-app` frontend client, and the
`RECRUITER` and `ADMIN` realm roles. The Angular client uses Authorization Code
with PKCE (`S256`) and has no client secret.

1. Start either the backend or frontend as described below; the Go runner
   starts Compose services and waits for PostgreSQL first.
2. Open `http://localhost:8080` and sign in to the Keycloak Admin Console using
   the local admin credentials from `docker/.env`.
3. Select `recruitment-realm`. Create a local test user under **Users**, set a
   password under **Credentials**, and assign the `RECRUITER` realm role under
   **Role mapping**.
4. Do not commit test-user credentials. The realm export deliberately contains
   no users or passwords.

The Compose setup imports the realm when it is absent and persists Keycloak
data in the `keycloak-data` named volume. It does not overwrite an existing
realm when that volume already contains one.

### Existing local Keycloak data warning

The Keycloak container that was running before the `keycloak-data` volume was
added used an unmounted container filesystem. Recreating it to apply the new
Compose mount may lose its existing realm and test users. If that container
still contains data you need, export or recreate the local users before
applying the updated Compose configuration.

Do not use `docker compose down -v` for routine cleanup; it deletes the named
database and Keycloak data volumes.

## Run backend and frontend

Open two PowerShell terminals at the repository root.

Terminal 1 — backend API:

```powershell
go run .\tools\verify.go -mode backend
```

The backend listens on `http://localhost:8081`. Stop it with **Ctrl+C**.

Terminal 2 — Angular frontend:

```powershell
go run .\tools\verify.go -mode frontend
```

Open `http://127.0.0.1:4200` and sign in with the Keycloak recruiter user.
Stop the frontend with **Ctrl+C**.

Both run modes start or reuse Docker Compose services. To run checks instead of
the servers:

```powershell
go run .\tools\verify.go
```

## Try the recruiter workflow

Use synthetic resume content and an `@example.com` address; do not use real
candidate data for local testing.

1. Sign in to the Angular frontend with the recruiter account.
2. Upload a synthetic PDF and note the candidate ID shown by the UI.
3. Trigger **Run Batch Ingestion Job** and wait for its status to finish.
4. Create a job description containing requirements related to the synthetic
   resume.
5. Run **Find Matching Candidates**. The dashboard should show matching
   candidates or explain that none met the configured similarity threshold.

The Spring Batch status reports the job execution, while candidate ingestion
status is tracked separately. Vector similarity is only a discovery signal;
recruiters must review evidence and make their own decisions.

## Local endpoints

- Angular: `http://127.0.0.1:4200`
- Backend API: `http://localhost:8081`
- Keycloak: `http://localhost:8080`
- PostgreSQL: `localhost:5432`
- Ollama: `http://localhost:11434` (must be running separately with
  `mxbai-embed-large` available for resume embedding and matching)
