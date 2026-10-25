# Leaky Bucket Limiter

A Spring Boot REST API that demonstrates per-user leaky-bucket request limiting and per-caller HTTP request limiting.

## Requirements

- Java 21
- No separate Maven installation is needed; use the included Maven Wrapper.

Run commands from the `LeakyBucketLimiter` project directory. On Windows use `mvnw.cmd`; on macOS/Linux use `./mvnw`.

## Run the application

```powershell
.\mvnw.cmd spring-boot:run
```

```bash
./mvnw spring-boot:run
```

By default the API listens on `http://localhost:8080`.

## API

Every request must include the configured caller identity header (`X-Caller-Id` by default). The value should uniquely identify the calling client and is used for per-caller HTTP rate limiting.

### `POST /allow`

Checks whether a request for a user is allowed, then returns the decision and current bucket snapshot. The request timestamp is Unix epoch **milliseconds**.

Example request:

```http
POST /allow
Content-Type: application/json
X-Caller-Id: client-123
X-Trace-Id: trace-abc
```

```json
{
  "userId": "user1",
  "timestamp": 1893456000000
}
```

Allowed response (`200 OK`):

```json
{
  "allowed": true,
  "user_id": "user1",
  "bucket": {
    "level": 1.0,
    "capacity": 5,
    "leak_rate": 1.0,
    "last_updated_at": 1893456000000.0
  }
}
```

When the bucket is full, the API returns `422 Unprocessable Content`; `allowed` is false and `reason` is included:

```json
{
  "allowed": false,
  "user_id": "user1",
  "bucket": {
    "level": 5.0,
    "capacity": 5,
    "leak_rate": 1.0,
    "last_updated_at": 1893456000000.0
  },
  "reason": "bucket_capacity_exceeded"
}
```

A timestamp must be strictly later than the server time; current or past timestamps and a missing/blank `userId` are rejected with `400 Bad Request`.

### `GET /users/{user_id}/bucket`

Returns the current bucket snapshot. For example, after `user1` has made an allowed request, the endpoint returns `200 OK` with its bucket details:

```json
{
  "user_id": "user1",
  "bucket": {
    "level": 1.0,
    "capacity": 5,
    "leak_rate": 1.0,
    "last_updated_at": 1893456000000.0
  }
}
```

A user without a bucket receives `422 Unprocessable Content` with a null `bucket`:

```json
{
  "user_id": "user1",
  "bucket": null
}
```

Example request:

```http
GET /users/user1/bucket
X-Caller-Id: client-123
```

## Swagger / OpenAPI

Springdoc generates the API description from the running application; there is no committed static Swagger YAML file. Start the app, then open:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- OpenAPI YAML: <http://localhost:8080/v3/api-docs.yaml>

The generated document shows the endpoint schemas. Request and response examples are shown above; use the OpenAPI endpoints or Swagger UI to inspect the generated schema. The caller identity filter applies to these URLs too, so include `X-Caller-Id` when fetching Swagger UI or OpenAPI documents. To save a generated YAML snapshot in the project directory:

```powershell
Invoke-WebRequest -Headers @{ 'X-Caller-Id' = 'swagger-docs' } `
  http://localhost:8080/v3/api-docs.yaml -OutFile swagger.yaml
```

The OpenAPI document is generated from controller/model metadata; the project does not currently define explicit OpenAPI example annotations. The full request and response examples are maintained in this README.

## Configuration

Values are loaded from `src/main/resources/application.properties` and can be overridden with Spring Boot environment variables or external configuration, without rebuilding the application.

| Property | Default | Purpose |
|---|---:|---|
| `bucket.capacity` | `5` | Maximum number of pending requests in a user's leaky bucket. |
| `bucket.leak-rate` | `1.0` | Bucket units removed per second. |
| `rate-limit.header-name` | `X-Caller-Id` | Required request header used to identify a caller. |
| `rate-limit.max-requests` | `5` | Maximum requests per caller in a fixed window. |
| `rate-limit.window-ms` | `1000` | Fixed-window duration in milliseconds. |
| `trace-id.header-name` | `X-Trace-Id` | Header used to pass or return a trace ID. |

For example, set environment variables before running to allow 10 requests per caller in a 2-second window:

```powershell
$env:RATE_LIMIT_MAX_REQUESTS = "10"
$env:RATE_LIMIT_WINDOW_MS = "2000"
.\mvnw.cmd spring-boot:run
```

The request rate limiter is a **fixed-window** limit, not a sliding-window limit. Requests above the configured limit receive `429 Too Many Requests`. A missing or blank caller header receives `400 Bad Request`.

## Error handling

- Invalid request fields (including future timestamps) and a missing/blank caller header return `400 Bad Request`.
- Exceeding the per-caller HTTP request limit returns `429 Too Many Requests` with `{"error":"Rate limit exceeded"}`.
- A bucket-capacity rejection returns `422 Unprocessable Content` with `allowed: false` and `reason: "bucket_capacity_exceeded"`.
- `GET /users/{user_id}/bucket` catches `UserNotFoundException` and returns `422 Unprocessable Content` with `bucket: null`.
- `ApiExceptionHandler` centralizes structured responses for validation errors and registered application exceptions. `BucketOverflowException` is mapped to `403 Forbidden` if raised; the normal allow flow returns a `422 Unprocessable Content` response for capacity rejection.

The request body uses Bean Validation annotations and a custom `FutureTimestampValidator` to require a timestamp strictly later than the server time. Null is permitted by the custom validator so the `@NotNull` constraint can report the missing timestamp.

## Design decisions and assumptions

- **Caller identity:** The design assumes every API caller provides a nonblank `X-Caller-Id` (or configured equivalent) that uniquely identifies it. The filter trusts this header; production deployments should ensure clients cannot freely impersonate other callers (for example, set/replace it at a trusted gateway after authentication).
- **Process-local state:** Caller rate-limit windows and user buckets are held in memory. They reset on restart and are not shared across application instances; multiple replicas require a shared state store or distributed rate limiter for consistent enforcement.
- **Single-threaded bucket updates:** The bucket controller replaces its map reference after each state transition and the service itself does not synchronize access. A standard servlet server may process requests concurrently, so this is suitable only where bucket mutations are serialized or for demonstration/single-threaded use. Deployments handling concurrent calls should use a thread-safe state boundary or external atomic storage.
- **Functional state transition:** `BucketLimiterService.allow(...)` accepts the current bucket map and configuration and returns an `AllowResult` with a new immutable map and the allow/deny decision. `BucketState` is an immutable record. The controller owns the current map reference and replaces it with the returned map. This limits mutation inside the limiter service; it does not make the whole HTTP application purely functional.
- **Dependency injection and configuration:** Spring still constructs and injects the controller, service, and configuration-property beans. `BucketProperties` is passed explicitly to the state transition rather than retained inside the service, making the limiter logic's inputs visible and easier to test. This combines Spring's dependency wiring with a functional core; it does not eliminate auto-wiring.
- **Timestamp semantics:** The request timestamp is epoch milliseconds and must be in the future at validation time. Bucket processing uses the later of the provided timestamp and current server time.
- **Trace and timing:** `TraceIdFilter` accepts `X-Trace-Id` or generates a UUID when it is absent/blank, returns it in the response header, and places it in SLF4J MDC. `RequestTimingFilter` logs method, path, status, elapsed milliseconds, and trace ID.

## Tests and coverage

The core limiter behavior is covered in:
- `src/test/java/com/moovup/LeakyBucketLimiter/service/BucketLimiterServiceTest.java`
- `src/test/java/com/moovup/LeakyBucketLimiter/controller/BucketControllerTest.java`

`BucketLimiterServiceTest` focuses on the business logic and state transitions for the leaky bucket. `BucketControllerTest` covers the REST API behaviors such as validation, success responses, and missing-user handling.

Run all tests:

```powershell
.\mvnw.cmd test
```

```bash
./mvnw test
```

Generate the JaCoCo HTML report and enforce the project coverage gate:

```powershell
.\mvnw.cmd verify
```

```bash
./mvnw verify
```

After `verify`, open `target/site/jacoco/index.html` (relative to the project directory) for the detailed report. The build requires at least **80% instruction coverage for the whole application bundle**; `test` runs tests but does not run the configured JaCoCo verification gate. Developers should run `verify` and satisfy the threshold before merging.

## Trade-offs

The trace and timing filters add a small amount of processing to each request, but provide request correlation and basic latency/status visibility that can help diagnose production behavior. The current logs are per-request measurements rather than aggregated metrics; a production deployment may additionally export metrics to a monitoring system. The in-memory, single-instance implementation keeps this sample simple but is not a distributed rate limiter.
