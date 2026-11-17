# TRUSTLAYER deployment and rollback guide

## Deployment status

The repository contains Dockerfiles, a Compose definition, Nginx configuration, health checks,
and a production-safety Spring profile. The Compose stack is intentionally loopback-only and is
suitable for a local hackathon demonstration.

Docker was not installed or available in the development environment when these files were added.
The YAML files parsed successfully, but no Docker image build, Compose startup, Nginx runtime, or
container health-check result exists yet. Treat the commands below as a documented deployment
procedure that still needs execution, not as a verified deployment.

## What the Compose stack starts

| Service | Role | Network exposure |
| --- | --- | --- |
| `mysql` | MySQL 8.4 with the persistent `mysql-data` volume | Internal Compose network only |
| `backend` | Spring Boot API and Flyway migration runner | Internal Compose network only |
| `frontend` | Unprivileged Nginx serving React and proxying `/api` | `127.0.0.1:8088` only |

The Compose profile is `docker`, not `prod`, because this local stack is plain HTTP on loopback.
Do not publish port 8088 through a router, tunnel, cloud firewall, or public host. Secure cookies
and real HTTPS are mandatory before a public deployment.

## Local Docker demonstration

### 1. Install and verify Docker Desktop

On macOS, install Docker Desktop from Docker's official distribution, start it, then verify the
engine rather than assuming the CLI is usable:

```zsh
docker version
docker compose version
```

Both commands must return client and engine information. If `docker version` reports that it
cannot connect to the daemon, Docker Desktop is not running yet.

### 2. Create local secrets

From the repository root:

```zsh
cp .env.example .env
openssl rand -base64 64
openssl rand -base64 64
```

Put the first generated value in `TRUSTLAYER_JWT_SECRET` and the second, different value in
`TRUSTLAYER_INDICATOR_HASH_SECRET` inside the ignored `.env` file. Also replace every sample
database password. Do not use the same value for the JWT secret, privacy-hash secret, MySQL app
password, and MySQL root password.

For the local HTTP-only demonstration, retain:

```properties
TRUSTLAYER_COOKIE_SECURE=false
TRUSTLAYER_EXPOSE_ACTION_TOKENS=false
```

Action-token exposure stays disabled even in this Compose demo. Email verification and password
recovery will therefore fail closed until an actual mail adapter is configured. That behavior is
intentional and safer than pretending an email was sent.

### 3. Build and start

```zsh
docker compose --env-file .env up --build
```

The first build downloads Maven, Node, and container dependencies. Wait for the backend health
check to become healthy, then open `http://127.0.0.1:8088`.

### 4. Verify the live local stack

In a second terminal:

```zsh
curl --include http://127.0.0.1:8088/api/health
curl --include http://127.0.0.1:8088/actuator/health
docker compose ps
```

The first endpoint should return the public API health envelope. The second should return 404
through Nginx because Actuator is deliberately not a public browser route. `docker compose ps`
should show all services healthy or running. Inspect a failed service with:

```zsh
docker compose logs --tail=200 backend
docker compose logs --tail=200 mysql
docker compose logs --tail=200 frontend
```

## Public-production requirements

Do not use the local Compose topology as an Internet-facing deployment. Before any public launch,
all of the following must be true:

1. Place the frontend and API behind a trusted HTTPS reverse proxy/load balancer and enforce HTTPS
   and HSTS at that edge.
2. Activate the `prod` Spring profile. It fails startup when cookies are insecure, action tokens
   are exposed, JWT and privacy-HMAC secrets match, or the JWT secret is weak.
3. Set `TRUSTLAYER_COOKIE_SECURE=true` and use a deliberate `SameSite` configuration. Keep the
   frontend and API on one HTTPS origin unless there is a reviewed cross-origin design.
4. Use a real secret manager. Do not deploy a `.env` file from a developer laptop or bake secrets
   into a container image.
5. Configure a transactional email provider before enabling registration and password recovery.
   The current repository has no production mail adapter.
6. Separate the application's database credentials from the Flyway migration credentials. The
   Compose demo uses one schema-owning app account only to keep a local demo manageable.
7. Enforce MySQL TLS, restrict database network access, encrypt backups, and test restoration on
   a disposable environment.
8. Replace in-memory login, scanner, transaction, and report rate limits with a shared gateway or
   Redis-backed implementation before running more than one backend instance.
9. Configure structured-log shipping, error alerting, uptime monitoring, and a tested incident
   response procedure. Never log JWTs, refresh tokens, QR contents, or raw sensitive targets.
10. Run the remaining release gates: Docker runtime test, authenticated security scan, dependency
    scan with reachable CVE feeds, backup/restore test, and real browser/device matrix.

## Health checks and monitoring

- Internal backend probe: `GET /actuator/health`.
- Public application probe: `GET /api/health`.
- Docker health checks call each service locally and should be treated as liveness signals, not a
  full readiness or security assessment.
- Keep Actuator private. The Nginx configuration returns 404 for `/actuator/` on the public
  frontend origin.

## Stop, rollback, and recovery

For a normal local stop that retains MySQL data:

```zsh
docker compose down
```

To roll back an application image after a known-good tag exists, update the deployment to the
known-good immutable image tag, run its database compatibility check, then restart only after the
change has been approved. Do **not** restore database data merely because an application image
changed; Flyway migrations are intentionally forward-only and require a documented recovery plan.

The following command permanently removes the local Docker database volume and therefore destroys
the demo data. Use it only when intentionally resetting a non-production demo environment:

```zsh
docker compose down -v
```

Production recovery requires tested encrypted backups, point-in-time recovery where appropriate,
least-privilege credentials, and an incident-specific decision log. Those operational controls do
not exist in this development workspace yet.
