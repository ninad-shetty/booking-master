# Booking Master

Spring Boot ticket booking API with PostgreSQL-backed shows, seats, and reservations. Seat reservations use holds, per-user limits, idempotency keys, and a scheduled expiry job.

## Requirements

- Java 21 (for running Maven locally)
- Docker Desktop with Docker Compose (recommended for local app + database)
- PostgreSQL 17 if running the app outside Docker

## Run Locally with Docker Compose

From the repository root:

```powershell
docker compose up --build
```

The API is available at `http://localhost:8080`; PostgreSQL is published at `localhost:5433`. Compose waits for the database health check before starting the app. Database data is kept in the `postgres_data` volume.

To stop the containers, press Ctrl+C. To remove containers and the network while retaining database data, run `docker compose down`. **Do not use `docker compose down -v` unless you intend to delete the database volume.**

## Run with Maven

Start PostgreSQL separately with the local database settings shown in `compose.yaml`, then run:

```powershell
./mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw spring-boot:run`. The default local JDBC URL is `jdbc:postgresql://localhost:5433/bookingmaster`.

Run tests with:

```powershell
./mvnw.cmd test
```

## Configuration

| Variable/property | Purpose | Local default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL; Compose sets the container address | `jdbc:postgresql://localhost:5433/bookingmaster` |
| `DB_USER` | Database username | `bookingmaster` |
| `DB_PASSWORD` | Database password | `bookingmaster` |
| `ADMIN_TOKEN` | Required `X-Admin-Token` for creating shows | `admin` (development only) |
| `PORT` | HTTP listen port | `8080` |
| `app.hold-minutes` | Reservation hold duration | `10` minutes |
| `app.expiry-interval-ms` | Delay between expiry-job runs | `5000` ms |

Do not use the local default credentials or admin token in production. Configure secrets through the deployment environment, not source control.

## API

All routes are at the service root. JSON request fields use snake_case.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Health check |
| `GET` | `/shows` | List shows with seat counts/statuses |
| `GET` | `/shows/{showId}` | Get one show and its seats |
| `POST` | `/shows` | Create a show and its seats; requires `X-Admin-Token` |
| `POST` | `/shows/{showId}/reserve` | Hold seats; requires `Authorization: Bearer <user-id>` and `Idempotency-Key` |
| `POST` | `/reservations/{id}/confirm` | Confirm a held reservation; requires bearer user ID |
| `POST` | `/reservations/{id}/cancel` | Cancel a reservation; requires bearer user ID |

Example create-show body:

```json
{
	"name": "Concert",
	"seats": ["A1", "A2", "A12"],
	"price_paise": 25000,
	"per_user_limit": 4
}
```

Example reserve body:

```json
{
	"seats": ["A12"]
}
```

The current bearer format treats the value after `Bearer` as the user ID; it is not a JWT. Use a unique idempotency key per distinct reservation request. See [POSTMAN_TEST_INPUTS.md](POSTMAN_TEST_INPUTS.md) for copyable Postman requests, headers, expected responses, and negative cases.

## Reservation Behavior

- A seat moves from `AVAILABLE` to `HELD` when reserved.
- A hold expires after `app.hold-minutes` unless confirmed.
- The expiry job checks every `app.expiry-interval-ms`; logs are emitted when holds expire or the job fails, not on every successful empty run.
- Concurrent reservations are protected by atomic seat claims and database constraints.
- Repeating the same request with the same idempotency key replays the original reservation; using that key for a different request returns a conflict.

## Database

`src/main/resources/schema.sql` initializes the PostgreSQL tables at application startup. Statements use `IF NOT EXISTS` so startup can safely rerun them; this does not migrate existing table columns. Keep production database backups before applying schema changes.

## Deploy to Render

The root [`render.yaml`](render.yaml) Blueprint defines the Docker web service and private PostgreSQL database in the `My project / Production` environment. In Render, create or sync the Blueprint from the `main` branch and review the plan before applying it. The Blueprint supplies `DB_URL`, `DB_USER`, `DB_PASSWORD`, and a generated `ADMIN_TOKEN`.

The container entrypoint converts Render's `postgres://` or `postgresql://` connection string to JDBC form. For services and databases in the same Render region, the internal database URL should be used.

The Blueprint currently uses Render's free web and PostgreSQL plans for setup. Free Postgres databases expire after 30 days; switch to a paid database plan before relying on it for persistent data.