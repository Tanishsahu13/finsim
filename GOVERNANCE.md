# FinSim Governance

This document explains how decisions are made. It is deliberately short and will evolve as the community grows.

## Current model: maintainer-led

FinSim starts with a single lead maintainer, who makes final decisions when consensus isn't reached.

| Role | Person |
|---|---|
| Lead maintainer | @mhnuk2007 |

## Roles

- **Contributor:** anyone who opens an issue, sends a pull request, writes docs, or helps others.
- **Maintainer:** has merge rights and helps review. Maintainers are expected to respond to issues and PRs in their area and to follow the Code of Conduct.
- **Lead maintainer:** resolves disagreements, sets the roadmap priorities, and owns releases until a steering group exists.

## Becoming a maintainer

Sustained, high-quality contributions (code, reviews, docs, or community support) over a few months. An existing maintainer nominates you in a public issue, and the lead maintainer confirms unless there are objections. Maintainers who become inactive for six months are moved to emeritus status, and can return on request.

## How decisions are made

1. **Everyday changes:** one maintainer approval and passing CI.
2. **Contract, state machine, or locked-rule changes:** an ADR (see CONTRIBUTING.md), open for at least 7 days for comment, approved by the lead maintainer. These changes are breaking for users, so they get more scrutiny.
3. **Roadmap changes:** proposed via an issue labeled `roadmap`, discussed openly, decided by the lead maintainer.
4. **Disagreements:** discuss in the thread, then the lead maintainer decides and records the reasoning.

When there are two or more active maintainers beyond the lead, the project will move to a majority vote among maintainers for ADRs, with the lead holding a tie-break.

## Versioning and stability

- The `finsim/v1` contract is versioned independently of the platform.
- Until `1.0`, breaking changes can happen but require an ADR and a changelog entry.
- After `1.0`, the contract follows a deprecation policy with an overlap period for the previous version.

## Licensing and ownership

- Code is licensed under **Apache License 2.0**.
- Contributions are accepted under the same license, certified by DCO sign-off. There is no CLA, and copyright stays with the contributors.
- The project name and logo may be used to refer to the project accurately. Forks should use a different name if they diverge significantly. (TODO: confirm name availability and decide on trademark registration before launch.)

## Funding and money

If the project accepts sponsorship (for example via GitHub Sponsors or Open Collective), funds will be spent on project needs such as infrastructure, CI, and maintainer time, and reported publicly. Sponsors do not get decision rights.

## Changing this document

Changes to governance are proposed as a pull request and need approval from the lead maintainer after a 7-day comment period.
