# StellarApex Project

StellarApex is a modern, full-stack web application featuring a Spring Boot backend (
`antares-auth`), an Angular frontend (`sirius-app`), and a Spring Boot Admin server (`vega-admin`).
The entire stack is containerized with Docker and served securely via a Traefik reverse proxy.

## Tech Stack

- **Backend (Auth)**: Java 25, Spring Boot 3.5, Spring Security, JWT (`antares-auth`)
- **Backend (Admin)**: Spring Boot Admin (`vega-admin`)
- **Frontend**: Angular 20, TypeScript, Tailwind CSS 4, DaisyUI 5 (`sirius-app`)
- **Database**: PostgreSQL (`castor-db`)
- **Cache & Sessions**: Redis (`pollux-cache`)
- **Reverse Proxy**: Traefik (handles HTTPS, local domains, rate limiting)
- **Deployment**: Docker & Docker Compose

## Architecture Overview

All local traffic is managed by Traefik, which provides a single secure entry point (`https://...`)
and routes requests to the appropriate service.

``` 
(Your Machine)
        │
        ├─ Public Access ([https://stellar.apex](https://stellar.apex))
        │  │
        │  ├─ / (Routes Angular)      → [Traefik] → [sirius-app (Nginx)]
        │  └─ /antares/* (API)        → [Traefik] → [antares-auth (Spring)]
        │
        ├─ Admin Access
        │  ├─ [https://admin.stellar.apex](https://admin.stellar.apex)     → [Traefik] → [vega-admin (Spring)]
        │  └─ [https://proxy.stellar.apex](https://proxy.stellar.apex)     → [Traefik] (Internal Dashboard)
        │
        └─ Direct access (localhost only)
           ├─ localhost:5432  → [castor-db]
           └─ localhost:6379  → [pollux-cache]
```

## Prerequisites

- `JDK 25`
- `Node.js 24`
- `Docker`
- `openssl`(usually pre-installed on macOS/Linux)
- `htpasswd`(comes with`apache2-utils`on Linux, or use an online generator)

## Local Setup

### 1. Environment Configuration

Create an`.env`file in the project's root directory. This file contains all the sensitive variables
required to run the application.

```txt
# === CASTOR (PostgreSQL) ===
CASTOR_DB=
CASTOR_USERNAME=
CASTOR_PASSWORD=

# === POLLUX (Redis) ===
POLLUX_PASSWORD=

# === ANTARES (Auth Api) ===
# Generate with 'openssl rand -base64 64'
ANTARES_JWT_SECRET=
COOKIE_DOMAIN=

# === ADMIN USER ===
ADMIN_FIRSTNAME=
ADMIN_LASTNAME=
ADMIN_EMAIL=
ADMIN_PASSWORD=
```

### 2. Generate Local Certificates

We need self-signed certificates for Traefik to serve HTTPS locally.

```bash
# Create the certs directory
mkdir -p certs

# Generate the certificate for *.stellar.apex
openssl req -x509 -nodes -days 365 -newkey rsa:2048
-keyout certs/local.key -out certs/local.crt
-subj "/CN=*.stellar.apex"
```

### 3. Update Your Host File

Your computer needs to know that these domains point to your local machine.

- **macOS/Linux**: Edit`/etc/hosts`
- **Windows**: Edit`C:\Windows\System32\drivers\etc\hosts`

Add the following lines:

``` 
127.0.0.1 stellar.apex auth.stellar.apex admin.stellar.apex proxy.stellar.apex 
::1 stellar.apex auth.stellar.apex admin.stellar.apex proxy.stellar.apex 
```

### 4. Build and Run Containers

This command will build the service images and start all services.

``` bash
docker compose up --build -d
```

## Accessing the Application

Your stack is now running. Your browser will show a security warning, which is normal (self-signed
certificate). You can safely "proceed" or "accept the risk".

- **Sirius - Angular frontend served by Nginx**:`https://stellar.apex`
- **Antares - Auth and Users SpringBoot Api**:`https://auth.stellar.apex`
    - **Antares (Sirius)**:`https://stellar.apex/antares`
    - **Antares (Swagger UI)**:`https://stellar.apex/swagger-ui.html`
- **Vega - Admin SpringBoot Server**:`https://admin.stellar.apex`
- **Altair - Traefik Dashboard**:`https://proxy.stellar.apex`

**Database & Cache Access (Local Development)**

The databases are securely exposed_only_to`localhost`on your host machine.

- **Castor - PostgreSQL Database (`castor-db`)**:
    - **Host**:`127.0.0.1`
    - **Port**:`5432`
    - **User/Password**: (from your`.env`)

- **Pollux - Redis Cache (`pollux-cache`)**:
    - **Host**:`127.0.0.1`
    - **Port**:`6379`
    - **Password**: (from your`.env`)