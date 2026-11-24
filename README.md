# Stock Reservation API

I built this project to see what happens when two orders try to reserve the last unit at the same time. This Spring Boot API tracks available stock in one warehouse. Each reservation is for a single SKU.

## Run locally

Requirements are Java 21, Maven 3.9+, and Docker Compose. The database and API listen only on loopback in this milestone.

```shell
docker compose up -d --wait
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
```

The demo profile adds fictional inventory on a fresh database. SCANNER-01 starts with one unit. Omit the demo profile to apply the schema without sample records. If port 5432 is occupied, set STOCK_DB_PORT for Compose and update STOCK_DB_URL to use that port. STOCK_DB_USER and STOCK_DB_PASSWORD override the local database credentials.

## Try the API

Run these commands in PowerShell 7. The demo reserves the last unit, tries to reserve it again, and then cancels the original reservation twice.

```powershell
$api = "http://127.0.0.1:8080/api"
Invoke-RestMethod "$api/products/SCANNER-01"

$payload = @{ sku = "SCANNER-01"; quantity = 1 } | ConvertTo-Json
$reservation = Invoke-RestMethod "$api/reservations" -Method Post -ContentType application/json -Body $payload
Invoke-RestMethod "$api/products/SCANNER-01"

$null = Invoke-RestMethod "$api/reservations" -Method Post -ContentType application/json -Body $payload `
    -SkipHttpErrorCheck -StatusCodeVariable retryStatus
"Second reservation returned HTTP $retryStatus"

Invoke-RestMethod "$api/reservations/$($reservation.id)/cancellation" -Method Put
Invoke-RestMethod "$api/reservations/$($reservation.id)/cancellation" -Method Put
Invoke-RestMethod "$api/products/SCANNER-01"
```

On a fresh demo database, the stock calls return `1`, `0`, and `1`. The second reservation returns `409 Conflict`, and both cancellation calls return the same cancellation timestamp.

| Method and path | Result |
| --- | --- |
| GET /api/products/{sku} | 200 with available stock, or 404 |
| POST /api/reservations | 201 with a Location header, 400 for invalid input, 404 for unknown SKU, or 409 for insufficient stock |
| GET /api/reservations/{id} | 200 with the persisted reservation, or 404 |
| PUT /api/reservations/{id}/cancellation | 200 with the cancelled reservation on first and repeated calls, or 404 |

## Architecture and decisions

- Controllers validate HTTP inputs. The service owns transaction boundaries. Repositories use Spring JDBC to keep the SQL visible. Flyway versions the PostgreSQL schema.
- Reserving stock uses one conditional SQL update. It subtracts the requested amount only if enough stock is available, then writes the reservation in the same transaction. A failed insert rolls back the stock change. A separate read followed by an unguarded update would leave a race. A row lock with SELECT FOR UPDATE is another valid option when the business decision needs more data from the row first.
- Cancelling uses a conditional state transition. Only the transaction that sets cancelled_at restores stock. Repeated calls read the existing record. The cancellation and stock restore commit together.
- The schema enforces nonnegative stock, positive reservation quantities, and a valid product reference.

## Tests

Start a disposable PostgreSQL database with Compose and run:

```shell
mvn verify
```

The integration suite uses PostgreSQL and Spring MVC. It checks competing requests, repeated cancellation, input validation, missing records, and rollback if the reservation write fails. GitHub Actions is configured to run the same suite with a PostgreSQL service.

## Milestone boundaries

The first milestone is a local vertical slice. The next milestone adds operator and supervisor roles, reservation history, and an OpenAPI contract. The current endpoints expose fixture data and should be used only on the local machine. Request retries are safe for cancellation. Reservation creation represents a new request on each call. Future work includes an idempotency key for reservation creation, reservation expiry, and multiple warehouses.
