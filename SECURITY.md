# Security Policy

FinSim is a synthetic testing environment. It must never handle real funds, real card numbers, or real personal data. Even so, vulnerabilities in the platform (for example authentication bypass, injection, SSRF, tenant isolation, or anything that lets one participant affect another) are taken seriously.

## Supported versions

FinSim is pre-alpha. Only the `main` branch is supported.

## Deployment Boundary (Local Sandbox Only)

The platform API and registry administrative endpoints have **no authentication**. FinSim is designed strictly as a local development and testing sandbox and is **not safe to expose to the public internet** or untrusted networks. Deployments should remain restricted to `localhost` or isolated, non-routable simulation environments.

## Reporting a vulnerability

Please **do not** open a public issue.

Report privately through GitHub: <https://github.com/mhnuk2007/finsim/security/advisories/new>

Include what you found, how to reproduce it, and the impact you expect. We aim to acknowledge reports within a few days and to agree on a fix and disclosure timeline with you.

## Scope reminder

If you find real credentials, card numbers, or personal data anywhere in the repository, report it the same way so it can be removed.
