# Local PostgreSQL Setup & Flyway Migration Guide

## Overview
Java DSA Mastery uses PostgreSQL 16+ as its relational persistence store and Flyway for version-controlled database migrations.

---

## 1. Quick Start with Docker Compose (Recommended)

When Docker is installed on your development workstation:

```yaml
# docker-compose.yml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    container_name: javadsa-postgres
    environment:
      POSTGRES_DB: javadsa_mastery
      POSTGRES_USER: javadsa_user
      POSTGRES_PASSWORD: javadsa_local_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U javadsa_user -d javadsa_mastery"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

### Starting the Database:
```bash
docker compose up -d
```

---

## 2. Local Native PostgreSQL Service (e.g. Windows / macOS / Linux)

If PostgreSQL is installed natively:

1. Open `psql` or `pgAdmin` as the superuser (`postgres`).
2. Create the application user and database:
   ```sql
   CREATE USER javadsa_user WITH PASSWORD 'javadsa_local_password';
   CREATE DATABASE javadsa_mastery OWNER javadsa_user;
   GRANT ALL PRIVILEGES ON DATABASE javadsa_mastery TO javadsa_user;
   ```
3. Connect to `javadsa_mastery` and grant schema privileges:
   ```sql
   \c javadsa_mastery
   GRANT ALL ON SCHEMA public TO javadsa_user;
   ```

---

## 3. Running Flyway Migrations

### Automatic Migration on Application Startup
When running the Spring Boot application with persistence enabled:
```bash
java -Djavadsa.persistence.enabled=true \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/javadsa_mastery \
     -Dspring.datasource.username=javadsa_user \
     -Dspring.datasource.password=javadsa_local_password \
     -jar target/java-dsa-mastery-1.0.0-SNAPSHOT.jar
```
Flyway automatically executes all pending migrations from `src/main/resources/db/migration` upon container start.

### Manual Migration using Flyway CLI
If you have `flyway` installed:
```bash
flyway -url=jdbc:postgresql://localhost:5432/javadsa_mastery \
       -user=javadsa_user \
       -password=javadsa_local_password \
       -locations=filesystem:src/main/resources/db/migration \
       migrate
```

---

## 4. Environment Variables Reference

| Environment Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/javadsa_mastery` | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `javadsa_user` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `javadsa_local_password` | Database password |
| `JAVADSA_PERSISTENCE_ENABLED` | `false` (in Phase 2B, `true` in Phase 2C) | Activates JPA repositories & adapters |

---

## 5. Security & Append-Only Permissions in Production

To enforce append-only immutability for authoritative pedagogical evidence:

```sql
-- Production security hardening:
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE evidence_records FROM javadsa_user;
REVOKE DELETE, TRUNCATE ON TABLE attempts FROM javadsa_user;
GRANT SELECT, INSERT ON TABLE evidence_records TO javadsa_user;
GRANT SELECT, INSERT, UPDATE ON TABLE attempts TO javadsa_user;
```
