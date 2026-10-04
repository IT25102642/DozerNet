# Module ownership map

Each of the six major functions is owned end-to-end by one group member and lives in its
own package + template folder for easy identification. The `common/` package is shared
infrastructure maintained by the whole team.

| # | Module | Owner | Student ID | Java package | Templates | Branch |
|---|--------|-------|------------|--------------|-----------|--------|
| 1 | Customer Management (incl. customer notifications) | G. Ananian | IT25102642 | `com.dozernet.module1_customer` | `templates/customer/` | `feature/m1-customer-management` |
| 2 | Booking & Rental Management | Theekshana A.D. | IT25101810 | `com.dozernet.module2_booking` | `templates/booking/` | `feature/m2-booking-rental` |
| 3 | Fleet (JCB) Management | Jayawardhana A.G.D.L. | IT25100798 | `com.dozernet.module3_fleet` | `templates/fleet/` | `feature/m3-fleet-management` |
| 4 | Operator Management | Perera M.A.S.A. | IT25103606 | `com.dozernet.module4_operator` | `templates/operator/` | `feature/m4-operator-management` |
| 5 | Maintenance & Service Management | Ruvendi R.G.K. | IT25101701 | `com.dozernet.module5_maintenance` | `templates/maintenance/` | `feature/m5-maintenance-service` |
| 6 | Payment & Administration | Thathsara J.P.T. | IT25103683 | `com.dozernet.module6_payment` | `templates/payment/` | `feature/m6-payment-admin` |

## Shared infrastructure (`com.dozernet.common`)

| Area | Package | Purpose |
|------|---------|---------|
| Security | `common.security` | BCrypt, RBAC, role-based login redirect, current-user helper |
| Users | `common.user` | Shared `User` entity + repository for all roles |
| Notifications (shared publisher) | `common.notification` | Observer-pattern publish pipeline (in-app + email); customer inbox UI is Module 1 |
| Model | `common.model` | `BaseEntity`, `Role` enum |
| Validation | `common.validation` | Shared regex patterns (phone, NIC, plate, licence) |
| Exceptions | `common.exception` | Global handler + domain exceptions |
| Web | `common.web` | Landing/about/login pages; admin/owner/operator notification centre |

## Each module contains

```
moduleN_xxx/
├── entity/       JPA entities owned by this module
├── repository/   Spring Data repositories (Repository/DAO pattern)
├── service/      business logic + validation rules
├── web/          controllers (dashboards, CRUD screens)
└── dto/          form-backing objects with Bean Validation
```
