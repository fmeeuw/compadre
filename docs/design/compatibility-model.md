# Compadre Compatibility Model

## Overview

Compadre is an event-sourced schema evolution system. Unlike traditional schema formats (Avro, Protobuf, JSON Schema) where each version is a complete snapshot, Compadre models schema as a **log of explicit operations**. This enables precise reasoning about compatibility between any two versions.

## Core Concepts

### Schema Log

A schema is defined by an ordered sequence of **SchemaChanges**, each containing:
- A version identifier
- A timestamp
- A list of operations

```
SchemaLog = [Change_v1, Change_v2, Change_v3, ...]
```

To get the schema at version N, replay all changes from v1 to vN.

### Operations

Every schema modification is an explicit operation:

| Operation | Description |
|-----------|-------------|
| `AddField` | Add a new field to an object |
| `RemoveField` | Remove an existing field |
| `RenameField` | Rename a field (preserving semantics) |
| `MakeOptional` | Change a required field to optional |
| `MakeRequired` | Change an optional field to required |
| `ChangeType` | Modify a field's data type |
| `AddEnumValue` | Add a value to an enum type |
| `RemoveEnumValue` | Remove a value from an enum type |
| `SetDefault` | Set or change a field's default value |
| `RemoveDefault` | Remove a field's default value |

## Compatibility Types

### Backward Compatibility

**Definition**: New schema can read data written by old schema.

A consumer using schema **v2** can read data produced with schema **v1**.

```
Producer (v1) --> data --> Consumer (v2)  ✓
```

### Forward Compatibility

**Definition**: Old schema can read data written by new schema.

A consumer using schema **v1** can read data produced with schema **v2**.

```
Producer (v2) --> data --> Consumer (v1)  ✓
```

### Full Compatibility

Both backward and forward compatible between versions.

## Operation Compatibility Matrix

Each operation has known compatibility characteristics for both producers and consumers.

**Terminology:**
- **Old Producer**: Writes the version BEFORE this operation
- **New Producer**: Writes the version AFTER this operation
- **Old Consumer**: Reads into the version BEFORE this operation
- **New Consumer**: Reads into the version AFTER this operation

### Full Compatibility Matrix

| Operation | New Consumer reads Old Producer | Old Consumer reads New Producer | Notes |
|-----------|:------------------------------:|:------------------------------:|-------|
| `AddField(optional)` | ✓ use None | ✓ ignore field | Safe both ways |
| `AddField(required, default=X)` | ✓ use default | ✓ ignore field | Safe both ways |
| `AddField(required, no default)` | ✗ no value | ✓ ignore field | **Consumers must upgrade first** |
| `RemoveField(optional)` | ✓ ignore field | ✓ use None | Safe both ways |
| `RemoveField(required)` | ✓ ignore field | ✗ no value | **Producers must upgrade first** |
| `MakeOptional` | ✓ no change | ✓ no change | Safe both ways |
| `MakeRequired(default=X)` | ✓ use default if null | ✓ no change | Safe both ways |
| `MakeRequired(no default)` | ✗ if null | ✓ no change | **Consumers must upgrade first** |
| `RenameField(old→new, alias)` | ✓ read from old name | ✓ read from new name | Requires alias support |
| `RenameField(old→new, no alias)` | ✗ field not found | ✗ field not found | **Breaking both ways** |
| `ChangeType(widening)` | ✓ widen value | ✓ narrow if safe | e.g., Int→Long |
| `ChangeType(narrowing)` | ✗ may overflow | ✓ widen | e.g., Long→Int |
| `ChangeType(incompatible)` | ✗ | ✗ | e.g., String→Int |
| `AddEnumValue` | ✓ no change | ✓ ignore unknown | Safe both ways |
| `RemoveEnumValue` | ✓ no change | ✗ unknown value | **Producers must upgrade first** |
| `SetDefault` | ✓ no change | ✓ no change | Safe both ways |
| `RemoveDefault` | ✗ if needed for translation | ✓ no change | Depends on field presence |

### Upgrade Order by Operation

| Operation | Safe Upgrade Order |
|-----------|-------------------|
| `AddField(optional)` | Any order |
| `AddField(required, default)` | Any order |
| `AddField(required, no default)` | Consumers first, then producers |
| `RemoveField(optional)` | Any order |
| `RemoveField(required)` | Producers first, wait for retention, then consumers |
| `MakeRequired(no default)` | Consumers first (must handle null) |
| `RenameField(no alias)` | Coordinated deploy required |
| `ChangeType(incompatible)` | Coordinated deploy required |
| `RemoveEnumValue` | Producers first, wait for retention |

### Registry Validation Logic

```scala
def canNewConsumerReadOldProducer(op: Operation): Boolean = op match
  case AddField(_, required = true, default = None) => false
  case MakeRequired(_, default = None) => false
  case RenameField(_, _, alias = false) => false
  case ChangeType(_, from, to) => isWideningConversion(from, to)
  case _ => true

def canOldConsumerReadNewProducer(op: Operation): Boolean = op match
  case RemoveField(_, required = true) => false
  case RenameField(_, _, alias = false) => false
  case ChangeType(_, from, to) => !isWideningConversion(from, to) && isCompatible(from, to)
  case RemoveEnumValue(_) => false
  case _ => true

def validateOperation(op: Operation, context: RegistryContext): ValidationResult = {
  val newConsumerOk = canNewConsumerReadOldProducer(op)
  val oldConsumerOk = canOldConsumerReadNewProducer(op)

  (newConsumerOk, oldConsumerOk) match
    case (true, true) => Allowed("Safe in any order")
    case (true, false) => Allowed("Producers must upgrade first, then wait for retention")
    case (false, true) => Allowed("Consumers must upgrade first")
    case (false, false) => Rejected("Breaking change - coordinated deploy required")
}
```

## Version Compatibility Contract

### Producer Declaration

A producer declares the single version it writes:

```yaml
producer:
  schema: "user-events"
  writes: "2.0.0"
```

### Consumer Declaration

A consumer declares which versions it supports:

```yaml
consumer:
  schema: "user-events"
  supports: ["1.5.0", "2.0.0"]
```

### Contract Validation

A producer-consumer pair is **compatible** when:

```
producer.writes ∈ consumer.supports
```

## Code Generation: Explicit Model Per Version

Each schema version produces a precise, standalone model. This is the default and recommended approach.

### Generated Types

```scala
// v1.5.0 - generated from schema log replayed to v1.5.0
case class UserEventV1_5(
  id: Int,
  name: Option[String]
)

// v2.0.0 - generated from schema log replayed to v2.0.0
case class UserEventV2_0(
  id: Int,
  name: Option[String],
  email: String,            // Required - precise, no ambiguity
  phone: Option[String]
)
```

### Consumer Handling

Consumer declares supported versions and handles each explicitly:

```scala
// Consumer supports: ["1.5.0", "2.0.0"]
// Generated union type:
type UserEventReader = UserEventV1_5 | UserEventV2_0

// Consumer code handles each version:
def handle(event: UserEventReader): Unit = event match
  case v1: UserEventV1_5 =>
    // v1.5 data - no email field exists
    processLegacy(v1.id, v1.name)
  case v2: UserEventV2_0 =>
    // v2.0 data - email is guaranteed present
    processWithEmail(v2.id, v2.name, v2.email)
```

### Benefits of Explicit Models

| Aspect | Benefit |
|--------|---------|
| Precision | Required fields are required - no false optionality |
| Clarity | Each version's shape is obvious |
| Type safety | Compiler ensures all versions are handled |
| Simplicity | No complex derivation logic |
| Debugging | Clear which version you're working with |

### Writer Model

Producers write to a single version and get a precise writer type:

```scala
// Producer writes: "2.0.0"
case class UserEventWriter(
  id: Int,
  name: Option[String],
  email: String,              // Must provide - compiler enforces
  phone: Option[String]
)
```

## Future Extension: Unified Reader Model

> **Note**: This is a potential future extension, not part of the initial implementation.

For consumers who prefer a single type over pattern matching, Compadre could optionally generate a **unified reader model** that any supported version can be converted to.

```scala
// Opt-in unified model for supports: ["1.5.0", "2.0.0"]
case class UserEventUnified(
  id: Int,
  name: Option[String],
  email: Option[String],      // Optional because absent in v1.5.0
  phone: Option[String]
)

// Auto-generated converters:
extension (v: UserEventV1_5)
  def toUnified: UserEventUnified = UserEventUnified(v.id, v.name, None, None)

extension (v: UserEventV2_0)
  def toUnified: UserEventUnified = UserEventUnified(v.id, v.name, Some(v.email), v.phone)
```

This gives consumers a choice:
- **Explicit models** (default): Maximum precision, handle each version
- **Unified model** (opt-in): Convenience, accept some optionality

The unified model derivation rules would be:
- Field required in ALL versions → required
- Field exists in SOME versions → optional
- Type varies → widest compatible type or error

## Schema Versioning

### Version Timeline

```
Schema: user-events

v1.0.0  ──────────────────────────────────────►
        │ AddField(id, required)
        │ AddField(name, optional)

v1.1.0  ──────────────────────────────────────►
        │ AddField(email, optional)

v2.0.0  ──────────────────────────────────────►
        │ MakeRequired(email)
        │ AddField(phone, optional)

v2.1.0  ──────────────────────────────────────►
        │ AddField(address, optional)
```

### Replaying to a Version

To get the schema at any version, replay operations from the beginning:

```scala
def schemaAt(version: Version): Schema = {
  val changes = schemaLog.changes.takeWhile(_.version <= version)
  changes.foldLeft(Schema.empty) { (schema, change) =>
    change.operations.foldLeft(schema)(applyOperation)
  }
}

schemaAt("1.1.0")
// Result: { id: Int (required), name: String?, email: String? }

schemaAt("2.0.0")
// Result: { id: Int (required), name: String?, email: String (required), phone: String? }
```

### Breaking Change Detection

Because operations are explicit, Compadre can classify each change:

```scala
def isBreakingChange(change: SchemaChange): Boolean = {
  change.operations.exists {
    case RemoveField(_, _) => true
    case MakeRequired(_, _) => true  // Old data might have nulls
    case ChangeType(_, from, to) => !isWideningConversion(from, to)
    case _ => false
  }
}
```

This enables tooling to warn when publishing breaking changes.

## Migration Support

### Automatic Migrations

For some operations, Compadre can generate migration logic:

| Operation | Read Migration | Write Migration |
|-----------|----------------|-----------------|
| `RenameField(old, new)` | Try new name, fallback to old | Write both names |
| `AddField(f, default=d)` | Use default if missing | Include field |
| `MakeOptional` | Handle null | No change |
| `ChangeType(int→long)` | Widen value | No change |

### Migration Annotations

Operations can include migration hints:

```scala
RenameField(
  from = "userName",
  to = "username",
  migration = Alias("userName") // Keep reading old name
)

RemoveField(
  name = "legacyId",
  migration = DeprecatedSince("2.0.0") // Warn but allow
)
```

## Centralized Schema Registry + Kafka Integration

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Schema Registry                         │
├─────────────────────────────────────────────────────────────┤
│ Schemas:                                                     │
│   user-events: [v1.0 operations, v2.0 operations, ...]      │
│                                                              │
│ Producers:                                                   │
│   service-a → user-events-prod (writes v2.0)                │
│   service-b → user-events-prod (writes v1.0)                │
│                                                              │
│ Consumers:                                                   │
│   service-x ← user-events-prod (reads v2.0)                 │
│   service-y ← user-events-prod (reads v1.5)                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Kafka Cluster                           │
├─────────────────────────────────────────────────────────────┤
│ Topic: user-events-prod                                      │
│   retention: 7 days                                          │
│   versions_present: [v1.0, v2.0]  ← tracked per topic       │
│                                                              │
│   Partition 0:                                               │
│     offsets 0-1000:    v1.0 (expires in 2 days)             │
│     offsets 1001-5000: v2.0                                  │
└─────────────────────────────────────────────────────────────┘
```

### Message Version Tracking

Each message is tagged with its schema version (e.g., in headers):

```
Message {
  headers: { "schema-version": "2.0.0" }
  key: ...
  value: ...
}
```

Kafka (or a sidecar) tracks which versions exist per topic:
- Updated on produce (new version seen)
- Updated on retention cleanup (old version expired)

### Validation Checks

Every schema change or service registration triggers compatibility validation.

#### 1. Schema Change Validation

When publishing a new schema version:

```yaml
# Request: Add v2.0 to user-events schema
schema: user-events
new_version: "2.0.0"
operations:
  - MakeRequired(email)  # Breaking change!
```

```yaml
# Registry checks:
existing_consumers:
  - service-x (reads v1.5):
      can_translate v1.5 → v2.0? NO (MakeRequired is breaking)

# Result: REJECTED
# Reason: Consumer service-x cannot handle v2.0
#         MakeRequired(email) breaks backward compatibility
#         service-x would fail reading v1.5 data as v2.0
```

#### 2. Producer Update Validation

When a producer wants to change its version:

```yaml
# Request: service-a wants to write v2.0
producer: service-a
topic: user-events-prod
current_version: "1.0.0"
new_version: "2.0.0"
```

```yaml
# Registry checks:
consumers_of_topic:
  - service-x (reads v2.0): can read v2.0? YES
  - service-y (reads v1.5): can translate v2.0 → v1.5? YES (drop new fields)

# Result: ALLOWED
# All consumers can handle v2.0 data
```

#### 3. Consumer Update Validation

When a consumer wants to change its version:

```yaml
# Request: service-x wants to read only v2.0
consumer: service-x
topic: user-events-prod
current_version: "1.5.0"
new_version: "2.0.0"
```

```yaml
# Registry checks:
topic_contains: [v1.0, v2.0]   # From Kafka
active_producers: [v1.0, v2.0]

can_translate:
  - v1.0 → v2.0: NO (v1.0 data lacks required email field, no default)
  - v2.0 → v2.0: YES

# Result: REJECTED
# Reason: Topic contains v1.0 messages that cannot be translated to v2.0
#         Wait 2 days for v1.0 messages to expire, or add default to email
```

#### 4. Consumer Drop Version Validation

When a consumer wants to stop supporting an old version:

```yaml
# Request: service-x wants to drop v1.0 support
consumer: service-x
topic: user-events-prod
dropping: "1.0.0"
```

```yaml
# Registry checks:
topic_contains: [v1.0, v2.0]
v1.0_expires_in: 2 days

# Result: REJECTED
# Reason: Topic still contains v1.0 messages
#         Safe to drop v1.0 support in 2 days
```

### Translation Rules

The registry computes translation possibility from the operation log:

| Operation | Old→New (read old as new) | New→Old (read new as old) |
|-----------|---------------------------|---------------------------|
| AddField(optional) | Use None | Drop field |
| AddField(required, default=X) | Use default X | Drop field |
| AddField(required, no default) | **FAIL** | Drop field |
| RemoveField(optional) | Drop field | Use None |
| RemoveField(required) | Drop field | **FAIL** |
| RenameField(old, new) | Read from old name | Read from new name |
| MakeOptional | No change | No change |
| MakeRequired(default=X) | Use default if null | No change |
| MakeRequired(no default) | **FAIL** if null | No change |

### Deployment Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Schema    │     │  Producer   │     │  Consumer   │
│   Change    │     │   Deploy    │     │   Deploy    │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────┐
│              Schema Registry Validation              │
├─────────────────────────────────────────────────────┤
│ • Check all consumers can handle new schema         │
│ • Check all consumers can handle producer's version │
│ • Check consumer can handle topic's versions        │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
      ┌──────────┐          ┌──────────┐
      │ ALLOWED  │          │ REJECTED │
      │ Deploy   │          │ + Reason │
      └──────────┘          └──────────┘
```

### Safe Upgrade Path for Breaking Changes

When a breaking change is needed (e.g., AddField with required, no default):

```
1. Publish schema v2.0 with:
   AddField(email, required, default="legacy@example.com")

   ✓ Registry allows: translation possible with default

2. Deploy consumers that read v2.0

   ✓ Registry allows: consumers can translate v1.0 → v2.0 using default

3. Deploy producers that write v2.0

   ✓ Registry allows: all consumers handle v2.0

4. Wait for retention period (v1.0 messages expire)

5. Optional: Publish schema v2.1:
   RemoveDefault(email)

   ✓ Registry allows: no v1.0 data exists anymore
```

## Open Questions

- [ ] How to handle nested object operations? (e.g., AddField at path `address.street`)
- [ ] Wire format: self-describing (JSON) or positional (Protobuf-like)?
- [ ] How to version the schema log format itself?
- [ ] Should field names be reusable after removal, or permanently reserved?
- [ ] How to track version ranges in Kafka efficiently? (partition metadata, separate topic, external store?)
- [ ] Should validation be blocking (reject deploy) or advisory (warn but allow)?
- [ ] How to handle consumer groups where instances have different versions during rolling deploy?

## Next Steps

1. Define the full operation algebra (AddField, RemoveField, MakeRequired, etc.)
2. Implement schema replay: `schemaLog.replayTo(version) → Schema`
3. Implement translation rules: `canTranslate(fromVersion, toVersion) → Boolean`
4. Build schema registry with producer/consumer registration
5. Implement Kafka integration for version tracking per topic
6. Build validation layer for schema changes and deployments
7. Implement code generation: `Schema → case class` with translators

