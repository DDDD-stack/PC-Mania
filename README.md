# PCMania

Online store for new and used PC components (Albanian market, prices in ALL) with a custom PC build quote service.
Spring Boot 3.5 · Java 21 · MySQL 8 · Thymeleaf + Bootstrap 5 · Flyway.

## Running locally

Requirements: JDK 21+, MySQL 8.

```sql
CREATE DATABASE pcmania CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pcmania'@'%' IDENTIFIED BY 'pcmania';
GRANT ALL ON pcmania.* TO 'pcmania'@'%';
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Shop: http://localhost:8070
- Admin: http://localhost:8070/admin — with the `dev` profile the first start creates `admin` / `admin123`.

Flyway creates the schema and seeds categories, brands and three sample GPUs.

## Testing from the phone

`start-for-phone.cmd` (double-click) starts the same dev server but bound to the PC's address on the local
network instead of `localhost`: it detects the Wi-Fi IPv4, exports it as `BASE_URL` (so product photos in the
Android app resolve), offers to add the inbound firewall rule for port 8070 — scoped to `remoteip=LocalSubnet`,
so only devices on the same Wi-Fi can reach it — and prints the address to type into the app's **Serveri** field.
The phone must be on the same Wi-Fi, and the app signs in with the same username and password as `/admin`.

Closing the console window does not always stop the server - `mvnw` runs Java as a separate process
that keeps holding the port - so starting again used to fail with *Port 8070 was already in use*,
which reads like the site is broken when it is in fact still running. The script now notices this,
says whether what is on the port is PCMania, and offers to stop it, open it, or do nothing. Pass a
port to use a different one: `start-for-phone.cmd 8071` (it is exported as `PORT`, which is the same
variable the hosted deployment uses).

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/pcmania` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `pcmania` / `pcmania` | DB credentials |
| `DB_SCHEMA` | `pcmania` | Schema Flyway creates and uses. On Postgres this keeps the tables out of `public`, which is the schema Supabase exposes publicly |
| `DB_POOL_SIZE` | `5` | Maximum JDBC connections |
| `BASE_URL` | `http://localhost:8070` | **Public https URL.** Used for canonical links, Open Graph images and the sitemap — Facebook previews break if this is wrong |
| `UPLOAD_DIR` | `./uploads` | Image storage (`thumb/`, `medium/`, `full/`). Back this up |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / *(blank)* | Used only when no admin exists. Blank password → a random one is printed to the log once |
| `NOTIFY_EMAIL` | *(blank)* | Operator address for new orders / build requests |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` | | SMTP. Without `MAIL_HOST` notifications are only written to the log |
| `WHATSAPP_NUMBER` | `355690000000` | International format, digits only |
| `PHONE_DISPLAY`, `CONTACT_EMAIL`, `FACEBOOK_URL` | | Shown in header/footer/contact page |
| `COURIER_SHIPPING_LEK` | `500` | Courier fee (0 for products marked "Transport falas") |
| `COOKIE_SECURE` | `false` | Set to `true` behind TLS so the admin session cookie is never sent in clear |

## Deployment notes

- Run behind nginx/Caddy with TLS. `server.forward-headers-strategy=native` trusts `X-Forwarded-*` only from private-network proxies, which keeps rate limiting and login lockout keyed on the real client IP.
- Build: `./mvnw package` → `java -jar target/pcmania-1.0.0.jar`.
- After deploying, paste a product URL into the [Facebook Sharing Debugger](https://developers.facebook.com/tools/debug/) to confirm the preview (and to refresh Facebook's cache after changing photos or price).
- Submit `BASE_URL/sitemap.xml` in Google Search Console.

### Render

`Dockerfile` and `render.yaml` are in the repo; Render has no built-in Java runtime, so the service
runs as a container. Create the service with **runtime = Docker** (or use the blueprint) and set the
variables from the table above. Render injects `PORT` and `application.yml` already reads it.

Two things Render does not provide:

- **No managed database.** PCMania is deployed against **Supabase** Postgres. Take the *pooler*
  connection string (Project Settings > Database > Session mode) - the direct host is IPv6-only
  while the pooler answers on IPv4 - and set `DB_URL` / `DB_USER` / `DB_PASSWORD`. Flyway creates
  the schema on first start. Migrations live under `db/migration/{vendor}`, so the same build
  still runs against the local MySQL database; `spring.flyway.locations` picks the folder from
  the JDBC URL.
- **No persistent filesystem** unless the instance is paid and has a disk attached. `UPLOAD_DIR`
  defaults to `/var/data/uploads` in the image, which is where `render.yaml` mounts a disk once
  the commented `disk:` block is enabled. Without a disk, every deploy deletes the product photos.

Set `BASE_URL` to the address Render assigns (`https://<name>.onrender.com`). It is what the
sitemap, canonical links, Facebook previews **and the photo URLs the phone app loads** are built
from, so the app shows no images until it is right. In the app's **Serveri** field type the host
on its own (`pcmania.onrender.com`) - anything that is not an IP address is treated as https.

On the free plan the service sleeps after 15 minutes and the next visitor waits about a minute.
The app allows 30s for a call and 60s for login to cover that, but a customer arriving from a
Facebook link sees the delay too. The app's 35-minute background order check also wakes
the service, which uses up the monthly free instance hours.

### Supabase security

Supabase publishes every table in an exposed schema through PostgREST, reachable with the
**anon key**, which is designed to be public and ships inside client apps. A table sitting in
an exposed schema with row level security off is therefore world-readable, and usually
world-writable. Supabase's Security Advisor reports this as *RLS Disabled in Public*, and it
is an error rather than a warning for good reason.

PCMania never uses PostgREST - the app connects over JDBC with a database password, and no
Supabase API key exists anywhere in the server, the website or the phone app. On top of that
there are two deliberate defences:

1. **Nothing is in `public`.** Flyway creates the tables in `DB_SCHEMA` (default `pcmania`),
   and Supabase only exposes `public` and `graphql_public` unless a schema is added by hand
   under Project Settings > API. The Flyway history table lands there too, so the equivalent
   of the `__EFMigrationsHistory` finding cannot occur either.
2. **RLS is on anyway, with no policies** (`V6__supabase_hardening.sql`), and the PostgREST
   roles have their grants revoked. A table with RLS enabled and no policy returns nothing to
   everyone except its owner, so even an exposed schema would answer every query with an empty
   result. The app is unaffected: a table owner bypasses RLS unless `FORCE ROW LEVEL SECURITY`
   is set, and it is not.

This matters more here than the table names suggest. In `public` without RLS the anon key would
expose `admin_user.password_hash`, `api_token.token_hash`, every customer name, phone number and
address in `orders`, `build_request` and `upcoming_interest` - and `product.cost_lek`, the field
every DTO and template in this codebase is written to keep private.

Rules to keep it that way:

- **Any new table needs its own `ENABLE ROW LEVEL SECURITY` line** in a migration.
- **Never add `pcmania` to the exposed schemas** in Project Settings > API.
- **The `service_role` key bypasses RLS entirely.** It must never reach the website, the phone
  app or the repository. Nothing in PCMania needs any Supabase key at all.
- Do not enable Supabase Auth. Admin login is `admin_user` plus BCrypt, and turning Auth on
  only adds an attack surface and more advisor warnings.

## How things work

**Stock & orders.** Checkout is a single-item "buy now" flow (the data model supports multi-item orders). Placing an order locks the product row, decrements `quantity` and sets the product to `RESERVED` when it reaches 0. Order status transitions:

```
NEW → CONFIRMED | CANCELLED
CONFIRMED → SHIPPED | DELIVERED | CANCELLED
SHIPPED → DELIVERED | CANCELLED
```

Cancelling restores stock (RESERVED → ACTIVE). Delivering sets `deliveredAt` and marks the product `SOLD` once no other open order holds units of it.

**Sales outside the site.** Items sold directly on Facebook can be recorded from the product edit page ("Shitje jashtë faqes"). This creates a `DELIVERED` order with the actual negotiated price, so the dashboard stays complete. Marking a product `SOLD` via the status field alone does **not** count as a sale in reports.

**Dashboard.** A sale is an order item in a `DELIVERED` order. Cost and price come from the order item snapshots; days-to-sell = product `listedAt` → order `deliveredAt`. Price bands use the unit sale price. Margin averages are revenue-weighted; days-to-sell averages are per unit.

**Cost privacy.** Public controllers only pass `ProductCard` / `ProductDetail` view records, which have no cost field. `Product` entities are only used in admin templates.

**Mobile admin API.** The Android app (`Desktop\PCManiaApp`) uses `/api/v1`, secured with bearer tokens
(`POST /api/v1/auth/login` with the admin username/password). Tokens are stored hashed in `api_token`, expire after
60 days without use and are revoked by `POST /api/v1/auth/logout`. Endpoints: `summary`, `orders?group=new|active|done`,
`orders/{id}` (+ `/status`, `/notes`), `builds?group=new|active|done`, `builds/{id}` (PATCH status, quote, admin notes),
`products?group=active|reserved|hidden|sold&q=`, `products/{id}` (PATCH title, price, cost, quantity, condition,
blurb, status; DELETE, which returns 409 when orders reference the product), `upcoming` (GET/POST, plus PATCH and
DELETE on `{id}`), `upcoming/{id}/interest` and `upcoming/interest/{id}/notified`, and `meta` for the enum labels.
Each list has a matching `/counts`. PATCH bodies are partial: a field left out keeps its stored value. Editing a
product's title from the app deliberately leaves the slug alone, so links already shared on Facebook keep working.
Failed logins share the web admin's 5-attempt lockout.

**Së shpejti.** Stock that is bought or on its way but not sellable yet lives in `upcoming_product`, managed at
`/admin/upcoming` or from the phone (Inventari › Së shpejti). A teaser is `HIDDEN` (invisible), `VISIBLE` (shown on
`/se-shpejti` and the home strip) or `ARRIVED` (stays up with an "Ka ardhur" badge). Teasers are never orderable:
customers leave a name and phone, stored in `upcoming_interest`, and the operator calls them. A repeated phone number
for the same item is ignored rather than duplicated, and the same honeypot plus per-IP hourly limit as the other
public forms applies. Deleting a teaser deletes its waiting list.

**Abuse protection.** Public forms have a honeypot field and a per-IP limit (5 orders / 5 build requests per hour). Admin login locks an IP for 15 minutes after 5 failures.
