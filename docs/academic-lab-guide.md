# Academic & University Lab Curriculum Guide

Welcome, educators, researchers, and lab instructors! 

FinSim is designed as a **zero-liability, reproducible experimental laboratory** for teaching and researching distributed systems, interbank financial infrastructure, and resilience engineering.

Teaching financial systems is notoriously difficult because real banking rails are inaccessible, proprietary, and heavily regulated. FinSim provides universities with an open-source, reproducible testbed where students build real banking microservices, connect them across networks, deliberately break them, and verify that no money is created or lost.

---

## Ready-Made Course Modules & Lab Assignments

FinSim can be integrated as a multi-week project or a capstone assignment across three standard university courses:

### Track 1: Distributed Systems (Computer Science)
*Focus: Idempotency, ambiguous failure modes, network partitions, and state machines.*

- **Lab 1: The Fallacy of Network Reliability (`UNKNOWN` State Machine)**
  - **Objective:** Students observe why distributed transactions cannot simply be classified as `SUCCESS` or `FAILED`.
  - **Task:** Inject random HTTP timeouts during outbound credit calls using FinSim's fault engine. Students implement [ADR 0001 (Resolving UNKNOWN Transfers)](adr/0001-resolving-unknown-transfers.md) to query transaction status, distinguish between lost requests vs. lost responses, and prevent duplicate debit reversals.
- **Lab 2: Property-Based Verification of Zero Duplicate Financial Effects**
  - **Objective:** Mathematically verify that random chaos sequences never produce duplicate side effects.
  - **Task:** Write property-based tests using `jqwik` (Java), `Hypothesis` (Python), or `rapid` (Go) that bombard the payment network with duplicate deliveries, concurrent retries, and network delays to formally verify the conservation-of-value invariant.

---

### Track 2: Fintech Engineering & Financial Market Infrastructure (Fintech / IS)
*Focus: Payment rail architecture, ISO 20022 messaging, and double-entry accounting.*

- **Lab 3: Build an Independent Participant Bank (Cross-Language)**
  - **Objective:** Teach contract-first microservice development.
  - **Task:** Students are divided into teams and assigned different languages (Java/Spring, Python/FastAPI, Go, Node.js). Each team builds an independent participant bank complying strictly with the `contract/finsim-v1.openapi.yaml` specification.
  - **Evaluation:** Student banks run against the automated conformance suite (`finsim verify --url`) and must pass health checks, debit/credit legs, and idempotency conflict checks.
- **Lab 4: The Physical-World Discrepancy (ATM Partial-Dispense Trap)**
  - **Objective:** Reconcile asynchronous physical hardware events with digital ledgers.
  - **Task:** Simulate a customer cash withdrawal where the ATM dispenser jams midway after dispensing partial cash ($80 of a $200 debit). Students implement the compensating credit journal workflow to prove that physical delivery truth must be verified before accounting adjustments are posted.

---

### Track 3: Software Engineering & Cloud Architecture
*Focus: Spring Modulith, testcontainers, schema migrations, and CI automation.*

- **Lab 5: Modular Monolith vs. Microservice Boundary Enforcement**
  - **Objective:** Teach domain boundaries and architecture linting.
  - **Task:** Students use ArchUnit and Spring Modulith to enforce zero cross-module leakage between `finsim-core`, `finsim-registry`, `finsim-sim`, and `finsim-api`.
- **Lab 6: Ephemeral Chaos Snapshots & Deterministic Replay**
  - **Objective:** Eliminate cross-test pollution in CI pipelines.
  - **Task:** Implement sub-50ms in-memory state snapshot and restore mechanisms to reset virtual clocks, PRNG seeds, and ledger balances between scenario executions without restarting Docker containers.

---

## Syllabus & Lab Grading Rubric

| Criteria | Weight | Description |
|---|:---:|---|
| **Contract Compliance** | 30% | Participant microservice passes all 7 phases of `finsim verify`. |
| **Idempotency & Resilience** | 25% | System handles duplicate requests, retries, and timeouts without balance drift. |
| **Financial Integrity** | 20% | Strict adherence to decimal precision (no floating-point) and balanced double-entry journals. |
| **Test Quality & Coverage** | 15% | Unit tests, contract tests, and property-based chaos tests with Testcontainers. |
| **Code Modularity & Documentation** | 10% | Clear architecture documentation, signed commits (DCO), and clean build checks. |

---

## How to Cite FinSim in Academic Papers

If your lab or students publish research papers, theses, or technical reports using FinSim, please cite it using:

```bibtex
@software{FinSim2026,
  author = {Lal, Mohan},
  title = {{FinSim: An Open-Source Simulator for Financial Networks and Distributed Systems Resilience}},
  url = {https://github.com/mhnuk2007/finsim},
  version = {0.1.0-pre-alpha},
  year = {2026}
}
```

---

## Academic Partnerships & Guest Lectures

Are you an instructor interested in using FinSim in your upcoming semester?
- Open an issue or start a thread in [GitHub Discussions (Architecture & Design)](https://github.com/mhnuk2007/finsim/discussions).
- We are happy to assist instructors with sample solutions, guest lecture discussions, and custom test scenarios.
