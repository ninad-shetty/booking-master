# booking-master

## Deploy to Render

This repository includes a `render.yaml` Blueprint that creates a Docker web service and a private PostgreSQL database. In Render, create a new Blueprint from this GitHub repository and deploy the generated resources. Render generates the `ADMIN_TOKEN`; use its value as the `X-Admin-Token` header when creating shows.

The Blueprint uses Render's free PostgreSQL plan for initial setup. Free databases are temporary and expire after 30 days; choose a paid database plan in Render before relying on this deployment for persistent data.

For local development, start the app and database with `docker compose up --build`.