# Registry: institutions and networks

The registry knows which institutions exist, what they declared they can do, where to reach them, and which network they belong to. It lives in the `finsim-registry` module and owns the `registry_*` tables.

## Concepts

- **Institution:** an independently owned service (`id`, `name`, `type`, `baseUrl`, declared `capabilities`). Registering one does not run any of its code; FinSim only calls its contract endpoints.
- **Capability:** `ACCOUNT_DEBIT`, `ACCOUNT_CREDIT`, `STATUS_QUERY`. FinSim routes a call only to an institution that declared the matching capability.
- **Network:** a controlled environment with member institutions and currencies. Milestone 1 ships one seeded network, `sandbox` (USD only).
- **Scheme:** the rules a network applies. Milestone 1 has one hardcoded scheme, `sandbox-v1`, with one workflow, `wf.transfer.v1` (debit, then credit).

Everything is labeled `"provenance": "SYNTHETIC"`.

## HTTP API

| Method and path | Purpose |
|---|---|
| `POST /api/v1/institutions` | Register an institution. Idempotent by `id`: identical request returns `200`; a different definition for the same id returns `409`. First registration returns `201`. |
| `GET /api/v1/institutions` | List institutions |
| `GET /api/v1/institutions/{id}` | One institution (`404` if unknown) |
| `GET /api/v1/networks` | List networks |
| `GET /api/v1/networks/{networkId}` | One network, with `memberIds`, `currencies`, and `scheme` |
| `POST /api/v1/networks/{networkId}/members` | Add `{"institutionId": "..."}` to a network. Idempotent |

Registration is idempotent by its natural key instead of an `Idempotency-Key` header, because it is an administration call rather than a financial mutation.

Errors always look like `{"code": "...", "message": "..."}`:

| Code | HTTP | When |
|---|---|---|
| `INVALID_INSTITUTION` | 400 | Bad id, missing name or capabilities, or a rejected `baseUrl` |
| `INVALID_REQUEST` | 400 | Malformed JSON |
| `INSTITUTION_NOT_FOUND`, `NETWORK_NOT_FOUND` | 404 | Unknown id |
| `INSTITUTION_ALREADY_EXISTS` | 409 | Same id, different definition |
| `NOT_A_MEMBER`, `CAPABILITY_NOT_SUPPORTED` | 422 | Raised by routing checks inside FinSim |

## CLI

```bash
finsim register --id my-bank --name "My Bank" --base-url http://localhost:9100 \
  --capabilities ACCOUNT_DEBIT,ACCOUNT_CREDIT,STATUS_QUERY
finsim institutions                  # list
finsim institutions --id my-bank     # show one
finsim join --network sandbox --institution my-bank
finsim networks --id sandbox
```

The CLI talks to `http://localhost:8080` by default. Use `--finsim <url>` or the `FINSIM_URL` environment variable to change it. Output is the JSON FinSim returns; failures print the HTTP status and body to stderr and exit with code 1.

## Seed data

On startup FinSim creates the `sandbox` network and registers `bank-a` (`http://localhost:9001`) and `bank-b` (`http://localhost:9002`) as members, matching the Java sample bank's two profiles. The seed is idempotent and never overwrites an institution you changed.

Configure it under `finsim.registry.seed` in `application.yml`, for example to point at container hostnames in Docker Compose:

```yaml
finsim:
  registry:
    seed:
      enabled: true
      institutions:
        - id: bank-a
          name: Bank A (sample, Java)
          type: BANK
          base-url: http://bank-a:9001
          capabilities: [ACCOUNT_DEBIT, ACCOUNT_CREDIT, STATUS_QUERY]
```

Set `finsim.registry.seed.enabled: false` to start empty.

## Base URL safety (SSRF)

Institution URLs are attacker-controllable input, so they are validated by `BaseUrlValidator` at registration:

- Only `http` and `https`; no credentials or fragments in the URL.
- Trailing dots on hostnames (e.g. `localhost.`) are normalized to prevent fully qualified domain name (FQDN) filter bypasses.
- Link-local addresses (including the cloud instance metadata address `169.254.169.254`), unspecified addresses (`0.0.0.0`), and IPv6 link-local (`fe80::/10`) are **always** rejected.
- Alternative IPv4 representations (dword/integer, 2/3-octet short-form, octal, hex) and IPv4-mapped IPv6 literals (`::ffff:a.b.c.d`) are parsed and evaluated against link-local and loopback rules without issuing external DNS queries.
- Non-canonical IP formats and invalid numeric hostnames (such as numeric TLDs) are strictly rejected.
- With `finsim.registry.allow-private-addresses: false`, loopback (`127.0.0.0/8`, `::1`), private RFC 1918 ranges, carrier-grade NAT (`100.64.0.0/10`), IPv6 unique local (`fc00::/7`), and `localhost` (including `*.localhost`) are rejected too. The default is `true` because local development uses `localhost`. Set it to `false` for any shared or hosted deployment.

This check looks only at literal addresses and syntax to preserve offline determinism and fast startup. Protection against hostnames that dynamically resolve to private addresses must also be applied when FinSim executes outbound HTTP calls (see issue #10).

## Routing check

`Registry.requireRoutable(networkId, institutionId, capability)` is what the transfer workflow will call before sending a leg. It fails with `NOT_A_MEMBER` or `CAPABILITY_NOT_SUPPORTED`, which covers the registry side of issue 7. Mapping those to a rejected transfer happens when the workflow is built.

## Not built yet

Integration verification before an institution becomes active (`finsim verify`), suspending institutions, removing members, per-institution credentials, and configurable schemes.
