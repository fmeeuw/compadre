# Operation Upgrade Paths

This document analyzes each schema operation considering:
- Multiple producers (each writes one version)
- Multiple consumers (each reads one version)
- Storage (Kafka) containing multiple versions

## Operation: AddField (optional)

| Scenario | Old (v1) | New (v2) | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | Sends new field | Ignores unknown field | ✅ |
| Old producer → New consumer | Doesn't send field | Uses None | ✅ |
| Old data on Kafka → New consumer | No field | Uses None | ✅ |
| New data on Kafka → Old consumer | Has field | Ignores it | ✅ |

**Result:** ✅ Fully compatible - any upgrade order works

---

## Operation: AddField (required)

| Scenario | Old (v1) | New (v2) | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | Sends new field | Ignores unknown field | ✅ |
| Old producer → New consumer | Doesn't send field | **No value to use** | ❌ |
| Old data on Kafka → New consumer | No field | **No value to use** | ❌ |
| New data on Kafka → Old consumer | Has field | Ignores it | ✅ |

**Result:** Consumer must upgrade first, but cannot until:
- All old data expires from Kafka
- All producers upgraded

**Upgrade order:**
1. Wait for old data to expire OR use default value
2. Then consumer can upgrade
3. Then producer can upgrade (but producer going first is also fine)

---

## Operation: RemoveField (optional)

| Scenario | Old (v1) | New (v2) | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | Doesn't send field | Uses None | ✅ |
| Old producer → New consumer | Sends field | Ignores it | ✅ |
| Old data on Kafka → New consumer | Has field | Ignores it | ✅ |
| New data on Kafka → Old consumer | No field | Uses None | ✅ |

**Result:** ✅ Fully compatible - any upgrade order works

---

## Operation: RemoveField (required)

| Scenario | Old (v1) | New (v2) | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | Doesn't send field | **Expects field, fails** | ❌ |
| Old producer → New consumer | Sends field | Ignores it | ✅ |
| Old data on Kafka → New consumer | Has field | Ignores it | ✅ |
| New data on Kafka → Old consumer | No field | **Expects field, fails** | ❌ |

**Result:** Producer must upgrade first, but old consumers break

**Upgrade order:**
1. Consumer upgrades first (stops requiring the field)
2. Wait for all consumers upgraded
3. Producer upgrades (stops sending field)
4. Wait for old data to expire from Kafka

---

## Operation: MakeOptional

| Scenario | Old (v1: required) | New (v2: optional) | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | May send None | Expects value, but gets None | ⚠️ Maybe |
| Old producer → New consumer | Sends value | Handles as Option | ✅ |
| Old data on Kafka → New consumer | Has value | Handles as Option | ✅ |
| New data on Kafka → Old consumer | May be None | **Expects value** | ❌ |

**Result:** Consumer must upgrade first

**Upgrade order:**
1. Consumer upgrades (accepts Option)
2. Then producer can upgrade (may send None)

---

## Operation: MakeRequired

| Scenario | Old (v1: optional) | New (v2: required) | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | Sends value | Handles as Option | ✅ |
| Old producer → New consumer | May send None | **Expects value, gets None** | ❌ |
| Old data on Kafka → New consumer | May be None | **Expects value** | ❌ |
| New data on Kafka → Old consumer | Has value | Handles as Option | ✅ |

**Result:** Consumer must upgrade first, but cannot until old data gone

**Upgrade order:**
1. Wait for old data (with nulls) to expire from Kafka
2. All producers must already send non-null values
3. Then consumer can upgrade

---

## Operation: RenameField (old→new)

| Scenario | Old (v1: "userName") | New (v2: "username") | Works? |
|----------|----------|----------|--------|
| New producer → Old consumer | Sends "username" | **Looks for "userName"** | ❌ |
| Old producer → New consumer | Sends "userName" | **Looks for "username"** | ❌ |
| Old data on Kafka → New consumer | Has "userName" | **Looks for "username"** | ❌ |
| New data on Kafka → Old consumer | Has "username" | **Looks for "userName"** | ❌ |

**Result:** ❌ Breaking in all directions

**Upgrade order:** Coordinated deployment required (all at once)

---

## Summary Table

| Operation | Producer First? | Consumer First? | Wait for Kafka? | Breaking? |
|-----------|----------------|-----------------|-----------------|-----------|
| AddField(optional) | ✅ Either | ✅ Either | No | No |
| AddField(required) | ✅ OK | ❌ Needs old data gone | Yes | Partially |
| RemoveField(optional) | ✅ Either | ✅ Either | No | No |
| RemoveField(required) | ❌ Breaks old consumers | ✅ First | Yes (after) | Partially |
| MakeOptional | ❌ Breaks old consumers | ✅ First | No | Partially |
| MakeRequired | ✅ OK | ❌ Needs old data gone | Yes | Partially |
| RenameField | ❌ | ❌ | N/A | **Yes** |

---

## Safe Upgrade Paths

| Operation | Step 1 | Step 2 | Step 3 |
|-----------|--------|--------|--------|
| **AddField(optional)** | Anyone upgrades | Done | - |
| **AddField(required)** | Wait for Kafka retention | Consumer upgrades | Producer upgrades |
| **RemoveField(optional)** | Anyone upgrades | Done | - |
| **RemoveField(required)** | Consumer upgrades | Producer upgrades | Wait for Kafka |
| **MakeOptional** | Consumer upgrades | Producer upgrades | - |
| **MakeRequired** | Producers send non-null | Wait for Kafka | Consumer upgrades |
| **RenameField** | Coordinated deploy | - | - |
