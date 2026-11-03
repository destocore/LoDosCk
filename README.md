# 🛡️ LoDosCk
[![GPLv3](https://img.shields.io/badge/License-GPL-yellow.svg)](https://github.com/destocore/LoDosCk/blob/master/LICENSE)
# 📖 Language
**EN-UK** (Current)

[RU-RU](https://github.com/destocore/LoDosCk/blob/master/lang/ru-ru.md)

---

>[!IMPORTANT]
>**LoDosCk** is an ultra-lightweight, high-performance, asynchronous anti-DDoS and automated bot mitigation engine built specifically for **Velocity** proxy infrastructure. 
>
>The system is meticulously engineered to handle extreme High-Load network spikes. Operating within a restricted **1 GB RAM (Alpine Linux)** environment, the core architecture is capable of mitigating and holding between **2,700 and 6,000 concurrent rogue sessions** (depending on the active protection profile) due to zero-allocation data isolation, `O(1)` memory lookups, and the absolute elimination of blocking disk IO transactions during flood phases.

---

## 🚀 Key Features

*   ⚡ **Wire-Speed Filtering (L5-L7):** Intercepts and drops malicious handshakes during the early `PreLoginEvent` phase, preventing heavy JVM heap allocations.
*   🧠 **String Entropy Analysis:** Real-time linguistic evaluation of consecutive consonant/vowel densities to immediately flag randomized unstructured bot signatures.
*   💾 **Deferred Asynchronous IO:** Batched file serialization pipeline offloads blacklist syncs to disk every 30 seconds, eliminating I/O storage bottlenecks.
*   🔥 **L3 Kernel Integration:** Low-level hardware-speed protection via Netfilter hook bindings (`ipset` / `iptables`) generated directly from volatile RAM queues.
*   🛑 **Anti-Frame & Chat Exploitation Traps:** Linked session behavioral tracking that neutralizes advanced text-cloning botnets across multiple accounts without causing false-positive kicks to legitimate players.

---

## ⚙️ Configuration Specification

The default configuration layer initializes at: `plugins/lodosck/config.yml`

### 📊 Core System Modules

| Section Node | Parameter | Data Type | Description | Default |
| :--- | :--- | :---: | :--- | :---: |
| **KeenEyeAI** | `mode` | `int` | Security profile strictness:<br>`0` — Disabled (Mitigation hooks completely bypassed)<br>`1` — Pre-Login handshake linguistic inspection only<br>`2` — Full execution (Handshake profiling + real-time global chat deduplication) | `2` |
| **nick-analyzer** | `max-unstructured-length` | `int` | Length threshold for unstructured strings. Names longer than this value lacking separators (`_` or `-`) are quarantined. | `14` |
| | `max-consecutive-consonants` | `int` | Maximum dense consecutive consonant letters allowed (excluding filtered digraphs/trigraphs like *ch*, *sh*, *str*). | `4` |
| | `max-consecutive-vowels` | `int` | Maximum dense consecutive vowel letters allowed (excluding filtered digraphs/trigraphs like *eau*, *ee*). | `4` |
| **memory-guard** | `critical-ram-percentage` | `double` | Circuit Breaker JVM heap threshold (%). Forces immediate metadata cache evictions and drops rogue sessions to ensure proxy survival under memory exhaustion. | `95.0` |
| **IP-guard** | `max` | `int` | Connection pool allocation limit. Maximum concurrent active player nodes allowed from a single IP descriptor. | `3` |
| | `max-suspicious` | `int` | Maximum quota of flagged suspicious profiles permitted from a single subnet before triggering a total network drop. | `1` |
| **database-saver** | `flush-interval-seconds` | `int` | Worker task loop rate (in seconds) to flush the ephemeral RAM infraction queue to persistent text tables. | `30` |
| | `batch-size` | `int` | Maximum data allocation payload processed within a single batch file serialization transaction. | `50` |

### 💬 Custom Notification Payloads (`messages`)

| Parameter | Type | Functional Scope Description |
| :--- | :---: | :--- |
| `ban-reason` | `String` | Permanent disconnect string broadcasted to a client when its IP gets blacklisted. |
| `emergency-kick` | `String` | Disconnect payload sent to unverified sessions during aggressive memory load-shedding ballast purges. |
| `chat-flood-kick` | `String` | Terminal disconnect reason issued when an asset is dropped for spam flooding. |
| `chat-flood-warn` | `String` | Non-terminal inline text notification dispatched privately upon the first identical message copy. |

---

## ⚡ Linux Quick Start (L3 Hardware Mitigation)

You can establish low-level hardware protection inside the Linux Netfilter architecture. Execute these commands via your system bash shell:

```bash
# 1. Instantiate a high-speed kernel-level hash map structure
sudo ipset create lodosck_ban hash:ip

# 2. Append the ipset data structure to the Netfilter input chain to DROP traffic
sudo iptables -A INPUT -m set --match-set lodosck_ban src -j DROP

# 3. Establish a crontab entry to automatically load newly appended infraction data to the kernel map every minute
cat plugins/LoDosCk/banlist.txt | xargs -I {} sudo ipset add lodosck_ban {} 2>/dev/null
```

## 📱 All available versions

[![velocity-4.3.1 (BETA)](https://img.shields.io/badge/velocity%204.3.1-BETA-yellow.svg)](https://github.com/destocore/LoDosCk/blob/velocity-4.3.1)