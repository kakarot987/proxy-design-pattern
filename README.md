# Proxy Design Pattern — Report Generator

This Spring Boot project demonstrates the **Proxy** pattern: `ReportServiceProxy` implements the same `ReportService` contract as the simulated slow `ReportServiceImpl`, while adding access control through rate limiting and a response cache.

## Run

Requires Java 21.

```bash
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080) or request a report:

```bash
curl 'http://localhost:8080/reports?topic=java'
```

## Proxy behavior

- **20 requests per 60-second fixed window, per application instance**; rejected requests return HTTP **429**. The limit resets at the next window.
- **100 cached topics maximum** with least-recently-used eviction.
- **5-minute TTL** for each cached result.
- Identical topics share one generated result while cached. A synchronized proxy method prevents duplicate concurrent generation but **serializes all requests** in this teaching example.
- Blank report topics return HTTP **400**.
- The underlying report is simulated using a 3-second sleep.

These in-memory limits are **not shared between instances** and reset when the application restarts. For production use, consider a shared rate limiter and a mature cache library when throughput and horizontal scaling matter.

## Tests

```bash
./gradlew test
```

The unit tests cover cache reuse and expiration, capacity eviction, rate-limit rejection and reset, and invalid input.

## Why the Proxy pattern?

The controller requests `ReportService` behavior without implementing throttling or caching. The proxy wraps the real service and controls how calls reach it, making a costly operation faster on subsequent requests and adding one place for policy checks.
