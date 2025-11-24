# Vega Admin Server

Vega is the monitoring and administration server for the StellarAtlas platform. It utilizes Spring
Boot Admin to provide a centralized interface for managing registered microservices (such as
`antares-auth`), viewing logs, tracking metrics, and monitoring health status.

## Tech Stack

- **Framework**: Spring Boot 3.5
- **Core**: Spring Boot Admin Server 3.5
- **Language**: Java 25
- **Security**: Spring Security (Session-based, CSRF Protection, Remember-Me)

## Security Configuration

Vega is designed to run behind a secure reverse proxy and delegates authentication validation.

- **Forward Authentication**: Security is handled by **Traefik** and the **Antares Auth API**.
    - Requests reaching Vega must be pre-authenticated.
    - The application trusts the infrastructure and is configured to `permitAll()` requests
      internally, assuming the proxy has already enforced access control (User must be authenticated
      and have `ROLE_ADMIN`).
- **CSRF**: Enabled using `CookieCsrfTokenRepository` with `HttpOnly` set to false to allow
  client-side interaction.
- **Proxy Support**: Configured to trust `X-Forwarded-Proto` headers for correct HTTPS
  identification.

## Prerequisites

- Docker & Docker Compose
- A configured`.env`file in the project root.

## Environment Configuration

The following variables must be defined in the root`.env`file for the application to start:

| Variable         | Description                    |
|------------------|--------------------------------|
| `AMIN_USERNAME`  | Login username (email format). |
| `ADMIN_PASSWORD` | Login password.                |

## How to Run

This service is designed to be run as part of the full StellarAtlas stack via the root Docker
Compose.

## Accessing the Application

- **URL (Traefik)**:`https://admin.stellar.atlas`
- **Local Port**:`9091`(Exposed internally within the Docker network)