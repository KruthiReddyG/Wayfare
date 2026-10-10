# Smart Carpool Matching Platform

Wayfare is a beginner-friendly Spring Boot and MySQL carpool application. Drivers publish rides; passengers search for similar routes and times, request seats, and review completed journeys. Its frontend is plain HTML, CSS, JavaScript, and Bootstrap, served from Spring Boot's static resources.

## Project Structure

```text
database/schema.sql
src/main/java/com/example/carpool/
  controller/   REST endpoints and role gates
  dto/          validated API request/response records
  entity/       JPA entities for the nine database tables
  exception/    consistent API error responses
  repository/   Spring Data JPA repositories
  security/     stateless JWT filter, token service, security config
  service/      matching, authentication, ride lifecycle, demo data
src/main/resources/
  application.properties
  static/index.html, styles.css, app.js
src/test/java/com/example/carpool/MatchingServiceTest.java
```

## Requirements

- Java 17 or newer
- Maven 3.8 or newer
- MySQL 8.0 or newer

## Database and Setup

1. Create a MySQL user/database, or let the configured JDBC URL create `smart_carpool` for a user with database-creation permission. Alternatively, run `database/schema.sql` with a MySQL account that can create databases and tables.
2. Set database credentials before starting. Defaults are `root` / `root` for local development. PowerShell example:

   ```powershell
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "your-local-mysql-password"
   ```

3. From the project root, start the application:

   ```powershell
   mvn spring-boot:run
   ```

4. Open `http://localhost:8080`. Hibernate updates tables from the JPA entities. `database/schema.sql` is the explicit equivalent schema for manual setup. Demo fixtures are enabled by default; use `$env:SEED_DEMO = "false"` outside a local demo.

The connection is controlled by `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. For deployment, set a private long random `JWT_SECRET` (at least 32 bytes) and set `SEED_DEMO=false`. The checked-in JWT key and database defaults are development values only.

## Sample Accounts

All seeded accounts use `Carpool123!`:

| Role | Email |
| --- | --- |
| Driver | `driver@smartcarpool.local` |
| Passenger | `passenger@smartcarpool.local` |
| Admin | `admin@smartcarpool.local` |

Demo data includes a Hyundai i20 and a Hyderabad-to-Warangal ride two days from the first startup. The demo accounts are inserted only when the seed data is enabled and the admin account is absent. Registration cannot create an admin account.

## Main REST API

All request/response bodies use JSON. Authenticated calls send `Authorization: Bearer <token>`. Registration and login return the JWT and account summary.

| Method | Endpoint | Access / purpose |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | Public signup and login |
| POST | `/api/rides/search` | Public; JSON search request; returns descending 70/30 route/time scores |
| GET | `/api/rides`, `/api/rides/{id}` | Public ride browsing and details |
| POST | `/api/rides/vehicles` | Driver adds a vehicle |
| GET | `/api/rides/vehicles/mine` | Driver's vehicles |
| POST | `/api/rides` | Driver publishes a ride |
| GET | `/api/rides/mine` | Driver's rides |
| DELETE / POST | `/api/rides/{id}`, `/api/rides/{id}/complete` | Driver cancels/completes a ride |
| POST | `/api/requests` | Passenger requests a seat (`rideId`, optional `message`) |
| GET | `/api/requests/mine`, `/api/requests/inbox` | Passenger's requests / driver's inbox |
| PATCH / DELETE | `/api/requests/{id}` | Driver decides with `{"accepted":true}` / passenger cancels pending request |
| GET / DELETE | `/api/bookings/mine`, `/api/bookings/{id}` | Booking history / passenger cancels a confirmed seat |
| POST / GET | `/api/ratings`, `/api/ratings/mine` | Passenger rating / driver's reviews |
| GET / PUT | `/api/profile` | Current profile / update name and phone |
| GET | `/api/notifications` | Current user's notifications |
| POST | `/api/complaints` | Submit a complaint (`description`, optional `rideId`) |
| GET | `/api/admin/dashboard`, `/api/admin/users`, `/api/admin/rides`, `/api/admin/bookings`, `/api/admin/complaints`, `/api/admin/ratings` | Admin overview and management lists |
| PATCH | `/api/admin/users/{id}/active` | Admin block/reactivate account (`{"active":false}`) |
| DELETE | `/api/admin/rides/{id}` | Admin cancels a ride and notifies booked passengers |
| PATCH | `/api/admin/complaints/{id}/status` | Admin updates complaint to `OPEN`, `IN_REVIEW`, or `RESOLVED` |

Ride search JSON example:

```json
{
  "source": "Hyderabad",
  "destination": "Hanamkonda",
  "travelDate": "2030-06-10",
  "preferredTime": "08:30:00",
  "seats": 1
}
```

The route score is a normalized text similarity for both endpoints, weighted at 70%; departure-time proximity (up to a 3-hour window) contributes 30%. Results also require the requested date and enough seats. This intentionally simple matcher is suitable for a training/demo app, not a substitute for geocoding or road-network routing.

## Modules in Plain English

- **Entities and repositories:** map users, vehicles, rides, ride requests, bookings, payments, ratings, complaints, and notifications to MySQL.
- **Authentication/security:** BCrypt stores password hashes. Login returns a signed JWT. Endpoint and service checks enforce Driver, Passenger, and Admin actions.
- **Matching:** filters by date and seat count, scores route text and time proximity, then sorts best match first.
- **Ride lifecycle:** requests need driver acceptance. Acceptance and cancellation run in database transactions; a pessimistic ride lock prevents concurrent approvals from consuming the same last seat. A booking and pending cost-share payment record are created on acceptance; cancellation restores the seat and marks payment refunded.
- **Frontend:** responsive pages provide account creation, role-specific dashboards, ride search/results, posting, request management, journey history, notifications, profile/support, and admin tools.
- **Payments:** the app records the estimated share and status only. It does not charge cards or integrate a payment provider.

## Test and Verify

Run the matching tests with:

```powershell
mvn test
```

The included JUnit tests verify the Hyderabad/Warangal to Hanamkonda example and descending match-score order. Additional manual checks: try an incorrect password; search for a past date; request the same ride twice; have two passengers request the last seat and accept both; cancel a confirmed booking and check the seat returns; attempt driver-only actions as a passenger; complete a ride and submit one rating; log in as admin and deactivate/reactivate a user.

## API Test with PowerShell

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType 'application/json' -Body '{"email":"passenger@smartcarpool.local","password":"Carpool123!"}'
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/rides/search -ContentType 'application/json' -Body '{"source":"Hyderabad","destination":"Hanamkonda","travelDate":"2030-06-10","preferredTime":"08:30:00","seats":1}'
```

Use a date matching a ride in the current database; the seeded ride date is two days from startup. API errors return JSON such as `{"error":"This ride has no available seats"}`.