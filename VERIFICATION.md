# FluxPay verification status

This file distinguishes checks performed while packaging the project from checks that still require a machine with dependency/network access and Docker.

## Checks performed in the authoring environment

- Parsed `pom.xml` as XML.
- Parsed `frontend/package.json` and `frontend/tsconfig.json` as JSON.
- Parsed `docker-compose.yml` and `.github/workflows/ci.yml` as YAML.
- Parsed the frontend `.ts` and `.tsx` files using the globally available TypeScript transpiler. This is a syntax/transpile check, **not** a full React type-check or Vite production build.
- Compiled and ran a small standalone Java check for request-fingerprint canonicalization and balanced/unbalanced ledger rules.
- Checked that the archive contains example configuration, schema migrations, backend code, dashboard source, Docker files, CI and documentation.

## Checks not completed here

- `./mvnw test`: not completed because the Maven wrapper could not download the Maven distribution from Maven Central in this environment.
- `cd frontend && npm install && npm run build`: not completed; dependency installation did not finish in this environment. A direct `tsc` check without installed React/Vite packages also cannot complete.
- `docker compose up --build`: not run because Docker CLI is unavailable in this environment.
- End-to-end tests involving PostgreSQL, Kafka, Redis, and webhook endpoints: not run.

## First verification on a development machine

1. Install Docker Desktop and start it.
2. In the project root, copy `.env.example` to `.env` and set a unique local `DB_PASSWORD` and a random `APP_JWT_SECRET` of at least 32 bytes.
3. Run `docker compose up --build` and wait for the API and dashboard containers to start.
4. Open `http://localhost:4173`, register a demo account, create a simulated payment, and run reconciliation.
5. If a build or startup error appears, preserve the full first error and fix that before adding more features.

FluxPay is an educational payment sandbox. Provider outcomes are simulated; it is not a real payment processor and should not be used to handle real funds or production credentials.
