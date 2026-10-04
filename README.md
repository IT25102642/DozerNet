# DozerNet — Web-Based JCB Management System

DozerNet replaces phone-based JCB (construction equipment) rental coordination with one
verified, centralised platform connecting **customers, private owners, operators and
administrators**.

SE2030 Software Engineering group project — Group **MLB-B4G2-09**.

## Tech stack

| Layer      | Technology                                              |
|------------|---------------------------------------------------------|
| Language   | Java 21                                                 |
| Framework  | Spring Boot 3 (MVC, Data JPA, Security, Validation)     |
| Views      | Thymeleaf + a custom CSS design system (Apple-inspired) |
| Landing    | Hand-built hero: live fleet showcase + job-category panel |
| Database   | MySQL (via MySQL Workbench); H2 in-memory for dev/tests |
| Build      | Maven                                                   |
| Auth       | BCrypt password hashing + role-based access control     |

## Project structure (organised by member/module)

```
src/main/java/com/dozernet/
├── common/              shared: security, base entity, users, notification publisher,
│                        audit trail, document store, exceptions
├── module1_customer/    Customer Management (+ customer notifications) — G. Ananian (IT25102642)
├── module2_booking/     Booking & Rental           — Theekshana A.D. (IT25101810)
├── module3_fleet/       Fleet (JCB) Management      — Jayawardhana A.G.D.L. (IT25100798)
├── module4_operator/    Operator Management         — Perera M.A.S.A. (IT25103606)
├── module5_maintenance/ Maintenance & Service       — Ruvendi R.G.K. (IT25101701)
└── module6_payment/     Payment & Administration    — Thathsara J.P.T. (IT25103683)
```

See [MODULES.md](MODULES.md) for the full owner map and [docs/DESIGN_PATTERNS.md](docs/DESIGN_PATTERNS.md)
for the design patterns used.

## Running the app

### Option A — zero setup (H2 in-memory, default)

```bash
mvn spring-boot:run
```

Open http://localhost:8080. The H2 console is at http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:dozernet`, user `sa`, no password).

### Option B — MySQL (for the real demo / MySQL Workbench)

1. In MySQL Workbench, create the schema: `CREATE DATABASE dozernet;`
2. Update `src/main/resources/application-mysql.properties` with your MySQL username/password.
3. Run with the MySQL profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## Demo accounts

Seeded automatically on first run. Password for **all** demo accounts: `Password123`.

| Role     | Email                  |
|----------|------------------------|
| Admin    | admin@dozernet.lk      |
| Customer | customer@dozernet.lk   |
| Owner    | owner@dozernet.lk      |
| Operator | operator@dozernet.lk   |

## Business rules (confirmed with the client)

| Area | Rule |
|------|------|
| Pricing | Machine-specific daily rate + district transport surcharge (Rs. 2,000 Colombo &rarr; Rs. 10,000 Jaffna) + 18% VAT |
| Invoices | Numbered `INV-YYYY-00001`, itemised into hire / transport / VAT |
| Booking window | Same-day hires allowed, up to 180 days in advance |
| Conflicts | Date-range overlap per machine; several machines can be requested for one job |
| Cancellation | Pending: free &middot; approved but unpaid: until the start date &middot; paid: full refund over 48h before, 20% charge inside 48h, no refund once started |
| Payment | Demo card portal validates the number (Luhn), expiry and 3-digit CVV; part payments allowed with balance reminders |
| Operators | Assigned only after the invoice is paid in full; admin picks, the system flags who is free |
| Private owners | DozerNet keeps 10% commission; the rest is tracked as the owner's payout |
| Maintenance | Scheduling takes a machine off hire; anything unserviced for 3 months is flagged |
| Security | BCrypt, 8+ character passwords with an uppercase letter and a number, 30-minute session timeout |
| Accounts | One person can hold several roles (e.g. customer + private owner) |

## Testing

```bash
mvn test
```

100 tests: unit tests per module plus `SystemFlowIntegrationTest`, which drives the whole
pipeline end to end over HTTP (book &rarr; approve &rarr; invoice &rarr; pay &rarr; assign &rarr; complete
&rarr; inspect &rarr; audit).

## Notes

- **Build with JDK 21** (the version the project targets). Newer JDKs are not yet supported
  by the Mockito/Byte Buddy version Spring Boot 3.3 ships, and the unit tests will error:

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test    # macOS
  ```
- Currency is Sri Lankan Rupees (Rs.); phone numbers use the Sri Lankan mobile format.
- Uploaded documents (NIC / licence / ownership proof) are written to the `uploads/`
  directory, configurable with `dozernet.upload-dir`.
- Motor graders and dump trucks currently reuse an existing photo; drop real
  `motor-grader.jpg` / `dump-truck.jpg` files into `src/main/resources/static/images/machines/`
  and point `DataSeeder.TYPE_IMAGES` and `JobCategory.ALL` at them.
