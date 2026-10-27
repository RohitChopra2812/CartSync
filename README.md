# CartSync — Distributed Shopping Cart Service

Java 21 · Spring Boot 4 · Kafka · Redis · PostgreSQL · Docker

A production-inspired distributed shopping cart service demonstrating key distributed systems patterns:
- **Optimistic Locking** — concurrent cart updates without data corruption
- **Idempotent Order Placement** — Redis + DB two-layer dedup prevents double charges
- **Kafka Event Pipeline** — async fan-out to inventory and notification consumers
- **Dead Letter Queue** — automatic retry with DLT for failed event processing

---

## Prerequisites

- Java 21
- Maven (or use included `./mvnw`)
- Docker + Docker Compose

---

## Quick Start

### 1. Start infrastructure

```bash
docker compose up -d postgres redis zookeeper kafka
```

Wait ~10 seconds for Kafka to fully initialize.

### 2. Run the application

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/sapmachine-21.jdk/Contents/Home ./mvnw spring-boot:run
```

App starts on `http://localhost:8080`

Flyway runs migrations automatically on startup:
```
V1 — create cart tables
V2 — create order tables
V3 — create indexes
V4 — create processed_events table
```

---

## API Reference

### Cart

#### Get cart
```bash
curl http://localhost:8080/carts/{cartId}
```

#### Add or update item
```bash
curl -X POST http://localhost:8080/carts/{cartId}/items \
  -H "Content-Type: application/json" \
  -d '{"productId": 99, "quantity": 2}'
```

#### Remove item
```bash
curl -X DELETE http://localhost:8080/carts/{cartId}/items/{itemId}
```

#### Clear cart
```bash
curl -X DELETE http://localhost:8080/carts/{cartId}
```

---

### Order

#### Place order (idempotent)
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"userId": 1001, "cartId": 1}'
```

> `Idempotency-Key` header is required. Returns `400` if missing.

#### Get order
```bash
curl http://localhost:8080/orders/{orderId}
```

---

## Test Scenarios

### Setup — create a cart

```bash
docker exec -it cartsync-postgres-1 psql -U cartsync -d cartsync
```
```sql
INSERT INTO carts (user_id, session_id) VALUES (1001, 'session-abc');
\q
```

---

### Test 1 — Optimistic Locking

Add item and update quantity — version increments on each update:

```bash
# Add item (version: 0)
curl -X POST http://localhost:8080/carts/1/items \
  -H "Content-Type: application/json" \
  -d '{"productId": 99, "quantity": 2}'

# Update quantity (version: 1)
curl -X POST http://localhost:8080/carts/1/items \
  -H "Content-Type: application/json" \
  -d '{"productId": 99, "quantity": 5}'
```

Expected: second response shows `"version": 1`

---

### Test 2 — Idempotent Order Placement

Same `Idempotency-Key` returns same order without creating a duplicate:

```bash
# First request — creates order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"userId": 1001, "cartId": 1}'

# Second request — returns same order (no duplicate)
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"userId": 1001, "cartId": 1}'
```

Expected: both return same `id`, no new order created.

---

### Test 3 — Missing Idempotency-Key

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1001, "cartId": 1}'
```

Expected: `400 Bad Request` — `Missing Idempotency-Key header`

---

### Test 4 — Kafka Event Pipeline

Place a new order and watch server logs:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 660e8400-e29b-41d4-a716-446655440000" \
  -d '{"userId": 1001, "cartId": 1}'
```

Expected in server logs:
```
Published OrderPlacedEvent to topic=order.placed orderId=X
InventoryConsumer received event orderId=X cartId=1 — decrementing stock
NotificationConsumer received event orderId=X userId=1001 — sending email
```

---

### Test 5 — Dead Letter Queue

Verify Redis idempotency key:

```bash
docker exec -it cartsync-redis-1 redis-cli
GET order:550e8400-e29b-41d4-a716-446655440000
```

Expected: `"PLACED"`

Verify Kafka topic has messages:

```bash
docker exec -it cartsync-kafka-1 kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.placed \
  --from-beginning \
  --timeout-ms 5000
```

---

## Project Structure

```
src/main/java/com/cartsync/
├── cart/
│   ├── Cart.java                  # JPA entity, @Version column
│   ├── CartItem.java              # JPA entity, @Version + @JsonIgnore
│   ├── CartRepository.java        # @EntityGraph to prevent LazyInitializationException
│   ├── CartItemRepository.java
│   ├── CartService.java
│   ├── CartServiceImpl.java       # @Retryable on OptimisticLockException
│   ├── CartController.java
│   └── CartItemRequest.java
│
├── order/
│   ├── Order.java                 # idempotency_key UNIQUE constraint
│   ├── OrderRepository.java
│   ├── OrderService.java
│   ├── OrderServiceImpl.java      # Redis SET NX EX + DB fallback
│   ├── OrderController.java
│   └── OrderRequest.java
│
├── event/
│   ├── OrderPlacedEvent.java      # Kafka payload (Java record)
│   ├── OrderEventProducer.java    # publishes to order.placed topic
│   ├── InventoryConsumer.java     # group: inventory-svc, @RetryableTopic
│   ├── NotificationConsumer.java  # group: notification-svc, @RetryableTopic
│   └── DeadLetterHandler.java     # listens on order.placed.DLT
│
├── config/
│   ├── RedisConfig.java
│   ├── KafkaProducerConfig.java
│   └── KafkaConsumerConfig.java
│
└── filter/
    └── IdempotencyKeyFilter.java  # validates Idempotency-Key header
```

---

## Key Design Decisions

### Optimistic Locking
Cart updates use `@Version` column + `@Retryable(OptimisticLockException.class)` with exponential backoff (50ms → 100ms → 200ms). Checkout is intentionally NOT retried to prevent double charges.

### Two-Layer Idempotency
1. **Redis** `SET order:{key} NX EX 86400` — primary check, fast
2. **PostgreSQL** `UNIQUE` constraint on `idempotency_key` — fallback if Redis is down

### Kafka Consumer Groups
`InventoryConsumer` and `NotificationConsumer` use different `groupId` values so both receive every `OrderPlacedEvent` independently. Same groupId would cause them to compete for messages.

### DLT Retry Flow
```
order.placed → attempt 1 → fail
             → retry-1000 → attempt 2 (after 1s) → fail
             → retry-2000 → attempt 3 (after 2s) → fail
             → retry-4000 → attempt 4 (after 4s) → fail
             → order.placed.DLT → DeadLetterHandler logs error
```

### Per-Consumer Idempotency
`processed_events(event_id, consumer_group)` table ensures DLT replay doesn't cause double processing in already-successful consumers.

---

## Infrastructure

| Service | Image | Port |
|---|---|---|
| PostgreSQL | postgres:16 | 5432 |
| Redis | redis:7-alpine | 6379 |
| Zookeeper | confluentinc/cp-zookeeper:7.6.0 | 2181 |
| Kafka | confluentinc/cp-kafka:7.6.0 | 9092 |

---

## Database Schema

```sql
carts           (id, user_id, session_id, created_at, version)
cart_items      (id, cart_id, product_id, quantity, version)
orders          (id, user_id, status, idempotency_key, created_at)
processed_events(event_id, consumer_group, processed_at)
```
