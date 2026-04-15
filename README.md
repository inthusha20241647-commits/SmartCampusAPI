# SmartCampusAPI

A RESTful API for managing Rooms and Sensors across a university Smart Campus,
built using JAX-RS (Jersey 2.41) with an embedded Grizzly HTTP server.

---

## Technology Stack

- **Language:** Java 17
- **Framework:** JAX-RS (Jersey 2.41)
- **Server:** Grizzly HTTP Server (embedded)
- **JSON:** Jackson 2.15
- **Build Tool:** Maven
- **Data Storage:** In-memory (ConcurrentHashMap)

---

## Project Structure
src/main/java/com/smartcampus/
├── Main.java
├── SmartCampusApp.java
├── model/
│   ├── Room.java
│   ├── Sensor.java
│   └── SensorReading.java
├── store/
│   └── DataStore.java
├── resource/
│   ├── DiscoveryResource.java
│   ├── RoomResource.java
│   ├── SensorResource.java
│   └── SensorReadingResource.java
└── exception/
├── RoomNotEmptyException.java
├── RoomNotEmptyExceptionMapper.java
├── LinkedResourceNotFoundException.java
├── LinkedResourceNotFoundExceptionMapper.java
├── SensorUnavailableException.java
├── SensorUnavailableExceptionMapper.java
├── GlobalExceptionMapper.java
└── LoggingFilter.java
---

## How to Build and Run

### Prerequisites
- Java JDK 17 or above
- Maven 3.6+

### Step 1 — Clone the Repository
git clone https://github.com/inthusha20241647-commits/SmartCampusAPI.git
cd SmartCampusAPI

### Step 2 — Build the Project
mvn clean package

### Step 3 — Run the Server
java -jar target/ServerSocket-1.0-SNAPSHOT-jar-with-dependencies.jar

### Step 4 — Confirm Server is Running
You should see:
Smart Campus API started!
Available at: http://localhost:8080/api/v1
Press ENTER to stop the server...

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/ | Discovery — API metadata |
| GET | /api/v1/rooms | Get all rooms |
| POST | /api/v1/rooms | Create a new room |
| GET | /api/v1/rooms/{roomId} | Get room by ID |
| DELETE | /api/v1/rooms/{roomId} | Delete a room |
| GET | /api/v1/sensors | Get all sensors (optional ?type= filter) |
| POST | /api/v1/sensors | Register a new sensor |
| GET | /api/v1/sensors/{sensorId} | Get sensor by ID |
| GET | /api/v1/sensors/{sensorId}/readings | Get all readings for a sensor |
| POST | /api/v1/sensors/{sensorId}/readings | Add a new reading |

---

## Sample curl Commands

### 1. Get API Discovery Info
```bash
curl -X GET http://localhost:8080/api/v1/
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"LIB-301\",\"name\":\"Library Quiet Study\",\"capacity\":50}"
```

### 3. Get All Rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```

### 4. Create a Sensor
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"TEMP-001\",\"type\":\"Temperature\",\"status\":\"ACTIVE\",\"currentValue\":22.5,\"roomId\":\"LIB-301\"}"
```

### 5. Filter Sensors by Type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=Temperature"
```

### 6. Add a Sensor Reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d "{\"value\":24.5}"
```

### 7. Get All Readings for a Sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

### 8. Try to Delete Room With Sensors (409 Conflict)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 9. Create Sensor With Invalid RoomId (422 Error)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"TEMP-999\",\"type\":\"CO2\",\"status\":\"ACTIVE\",\"currentValue\":0.0,\"roomId\":\"FAKE-ROOM\"}"
```

---

## Conceptual Report

### Part 1 – Setup & Discovery

#### Question 1.1 – JAX-RS Resource Lifecycle & In-Memory Data Management

JAX-RS has a request-scoped lifecycle by default; every time an HTTP request 
is obtained, the runtime creates a fresh instance of every resource class.
The instance is immediately destroyed and garbage collected after the request
is fulfilled and the response is provided. In contrast to the singleton
lifecycle, which makes use of one copy of the class throughout every call for
the period of the application itself, this is behavior described by default
in the JAX-RS specifications.

This structural approach affects the internal memory information structures.
Any object-level fields specified inside the resource class, such as HashMap
or ArrayList, would be reset on every request that receives its own resource
instance. This would end up in the destruction of any previously saved data.
Therefore, avoiding this, the store of data needs to be kept away from the
resource class by itself, normally as static fields in a separate shared
class. This way, the data will continue to exist without regard to the number
corresponding to resource instances that are built or removed.

However, the problem of parallelism and racing scenarios emerges immediately
when shared static data is provided. A pair of threads trying to read and
write to the same structured data at the same time might end up in an
incorrect or damaged condition since JAX-RS performs multiple requests in
parallel across several threads. For instance, because HashMap is not thread
safe, the end result is ambiguous if two POST requests arrive concurrently or
if both aim to add a new Room. Because ConcurrentHashMap was developed
specifically for concurrent multi-threaded use and delivers atomic functions
without a requirement for external synchronization blocks, it is best used
in place of HashMap to manage this.

A room's sensorIds list must also be thread-safe, since concurrent POST
requests registering sensors to the same room could simultaneously modify it.
Replacing it with Collections.synchronizedList() or CopyOnWriteArrayList
prevents this. Alternatively, the entire resource class can be registered as
a @Singleton in JAX-RS, meaning one instance is shared across all requests,
but this makes thread-safety even more critical and requires explicit
synchronization on every write operation.

#### Question 1.2 – HATEOAS and the Value of Hypermedia in RESTful APIs

HATEOAS stands for Hypermedia as the Engine of Application State. It is
considered a hallmark of mature RESTful API design, as it embeds navigational
links directly inside API responses, allowing clients to discover available
actions dynamically rather than relying on hardcoded URLs or static external
documentation.

For client builders, there is a significant practical gain. Absent HATEOAS,
a programmer's comprehension of how to utilize the API is entirely reliant on
static documentation from outside. The manual becomes out of date if any
endpoint path, parameter, or structure is modified, and any client that
hard-coded such a path requires adjustment by hand. With HATEOAS, the client
just needs to follow the links that are dynamically displayed in each
response. A response for a newly created room might, for instance, contain
links to get the room, list all of its sensors, and remove it — all included
inside the body of the response. Since the API essentially defines its own
unique routing throughout runtime, this renders the API far less susceptible
to change, significantly reduces the likelihood of integration errors, and
reduces the learning process for new developers utilizing the API.

---

### Part 2 – Room Management

#### Question 2.1 – Returning Only IDs vs Full Room Objects in a List

When building the GET /api/v1/rooms endpoint, a key decision is whether to
return only room IDs or include complete room objects in a single response.

Returning just IDs makes the payload smaller and requires less bandwidth,
which are advantageous when dealing with large collections. To get full data,
the client must send a distinct GET /api/v1/rooms/{id} request for each room,
transferring the workload to the client. The N+1 problem results from this,
in which one initial request sets off N subsequent requests. This leads to a
broad increase in delays, server loads, and overall network consumption,
especially in high-traffic systems or mobile contexts.

Providing all necessary information in a single network round trip by
returning complete room objects solves the N+1 problem. Client-side
complexity will be reduced, enhancing apparent reply time, and it aligns with
RESTful principles of providing a full representation of resources. A
simplified version of each room containing key features such as ID, name, and
capacity while eliminating more complicated layers of information like sensor
details, which can be obtained separately with query parameters when required,
is an appropriate balanced decision.

#### Question 2.2 – Idempotency of the DELETE Operation

The DELETE operation is idempotent in this implementation, meaning that the
intended resource does not exist after the operation has been performed, no
matter the number of times the request is executed. This means that making
exactly the same DELETE request repeatedly produces the same server-side
result as executing it only one time.

The initial DELETE request for a specific room ID detects the room in the
data store, successfully completes the safety check confirming no sensors are
assigned, deletes the entry, and generates an HTTP 204 No Content response,
indicating successful removal with no body. If the exact same DELETE request
is made again, the service sends an HTTP 404 Not Found response since the
room no longer exists. Significantly, idempotency does not require the
response code to be identical for every call — all that is required is for
the server state to stay constant. The final result is identical in both
situations: the room is missing from the system. The process fully fits the
concept of idempotency as stated in the HTTP standard (RFC 9110) since there
are no unanticipated adverse effects on subsequent calls.

---

### Part 3 – Sensors & Filtering

#### Question 3.1 – Consequences of a Content-Type Mismatch with @Consumes

When a JAX-RS method is marked with @Consumes(MediaType.APPLICATION_JSON) it
specifies that it can only process request bodies formatted as
application/json. When the resource method is called, the JAX-RS runtime
identifies a mismatch if a client makes a request with a different
Content-Type header, such as text/plain or application/xml.

When the request body format is not supported, JAX-RS automatically replies
with an HTTP 415 Unsupported Media Type status code. The framework's content
negotiation mechanism takes care of this, so no manual validation code is
needed. The framework maintains the contract at the protocol level, and the
method's business logic may confidently assume it is consistently receiving
valid JSON. This is a clear separation of concerns. If @Consumes is omitted
entirely, JAX-RS tries to process any content type, which causes Jackson to
fail during deserialization, producing a null object or a 400 Bad Request —
much harder to diagnose than a clean 415.

#### Question 3.2 – Query Parameters vs Path Parameters for Filtering

Filtering with @QueryParam is more effective than integrating filter values
into the URL path. A path like /api/v1/sensors/type/CO2 may be misleading
since it indicates that CO2 is a specific, identifiable resource. On the
other hand, query parameters are accepted as optional modifiers on a
collection. In compliance with RESTful design principles, a URL such as
/api/v1/sensors?type=CO2 makes it clear that the client has requested the
sensors collection with an optional filter applied.

Furthermore, query parameters are much more flexible. Multiple filters can be
combined naturally, such as /api/v1/sensors?type=CO2&status=ACTIVE, without
requiring any changes to the resource path structure. Achieving the same with
path parameters would require defining new path patterns for every possible
filter combination, quickly rendering the routing logic unmaintainable. In
addition, query parameters are optional by nature — if no type parameter is
specified, the endpoint returns all sensors, which is the correct fallback
behavior. Due to these factors, the query parameter approach results in an
API architecture that is clearer, more extendable, and semantically valid.

---

### Part 4 – Sub-Resources

#### Question 4.1 – Architectural Benefits of the Sub-Resource Locator Pattern

The sub-resource locator pattern provides important architectural advantages,
particularly as an API gets larger and more complex.

The primary benefit is a clear separation of responsibilities. Each class has
a single, clearly stated job — the SensorReadingResource class deals only
with reading history for a particular sensor, while the SensorResource class
oversees sensor-level operations. As a result, each class is simpler to
comprehend, test separately, and manage on its own. The SensorReadingResource
class can be extended, tested, or modified in complete isolation without any
risk of breaking sensor management logic, which directly supports long-term
maintainability and makes unit testing significantly easier.

In contrast, defining all nested endpoints within a single large controller
leads to complexity as more sub-resources are added. Instead of a monolithic
controller, the sub-resource locator pattern splits the API hierarchy into
more focused classes, improving readability and reducing the risk of
introducing bugs. Furthermore, the pattern mirrors the real-world ownership
structure — a reading belongs to a sensor, so accessing readings through the
sensor resource reflects this logical relationship naturally.

---

### Part 5 – Error Handling

#### Question 5.2 – Why HTTP 422 is More Semantically Accurate than 404

HTTP 404 Not Found is logically wrong to return when a client makes a POST
request to create a new Sensor with a roomId that does not exist in the
system. A 404 response conventionally communicates that the requested URL was
not found on the server — it is a routing-level error meaning the path does
not exist. The endpoint /api/v1/sensors, however, is fully accessible and
operating as expected. The request body's actual content is the issue, not
the URL.

HTTP 422 Unprocessable Entity explicitly implies that the server recognises
the request format and was able to parse it, but the payload's semantic
content is invalid and cannot be processed. In this case, the endpoint
exists, the JSON body is syntactically correct, and the Content-Type is
accurate, but the roomId field's value refers to a missing resource, making
it impossible to fulfil the request. The 422 code gives the client direct
notice that the problem is with the data they sent, rather than the URL they
targeted. This provides actionable information — the client should correct
the roomId value in their request rather than changing the endpoint.

#### Question 5.4 – Security Risks of Exposing Java Stack Traces

Due to the OWASP Top 10, displaying raw Java stack traces in API responses is
a serious security vulnerability categorised as information leakage.

An attacker can extract a significant amount of sensitive internal information
from an exposed stack trace. A hacker may map the internal architecture and
identify which frameworks, libraries, and design patterns are being used by
examining the full class names and package structure. Furthermore, it reveals
library names and version numbers — knowing that the application uses Jackson
2.15 or Jersey 2.41 enables an attacker to search for publicly known CVEs for
those exact versions and exploit any unpatched vulnerabilities. Third, file
system paths on the server where classes are deployed are frequently revealed,
which can assist in crafting file inclusion or directory traversal attacks.
Fourth, line numbers and method names in a stack trace expose the
application's internal logic flow, enabling an attacker to craft malicious
inputs targeting specific code paths. Lastly, exception messages themselves
occasionally contain sensitive data such as database queries, configuration
values, or user input. The global ExceptionMapper<Throwable> acts as a
critical protective layer by intercepting unexpected errors and substituting
a generic, non-revealing 500 response in place of the full stack trace.

#### Question 5.5 – Why JAX-RS Filters are Superior to Manual Logging

Instead of manually adding Logger.info() calls into each resource method,
implementing a dedicated JAX-RS filter that implements both
ContainerRequestFilter and ContainerResponseFilter is a much more consistent
approach to logging. By its very nature, logging is a cross-cutting concern,
meaning it applies equally across all endpoints regardless of the specific
functionality being carried out. The framework efficiently intercepts every
incoming request and outgoing response by specifying this behaviour once
within a filter, eliminating the need to modify individual resource classes.
This follows the DRY principle and eliminates the risk that developer
oversight will result in missing log entries.

In contrast, manual logging introduces tight coupling between observability
concerns and business logic, leading to cluttered and less readable resource
methods. Any changes to logging behaviour — such as adding correlation IDs or
modifying the log format — would require updates across every single resource
method, significantly increasing maintenance overhead and the risk of
inconsistency. Filters centralise these changes in a single location and
provide a more accurate picture of the HTTP transaction, including the request
method, URI, and final response status code. The filter-based strategy is
therefore more scalable and compliant with recognised software engineering
best practices.
