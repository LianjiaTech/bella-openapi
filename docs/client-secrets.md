# Client secrets

Client secrets are short-lived JWT credentials for trusted clients that need to connect directly to Bella OpenAPI or to SPI-based capability services. They are issued by Bella OpenAPI, signed with an asymmetric private key, and verified locally by capability services using a statically configured public key. No signing secret is shared with capability services, and no per-secret state is stored centrally except the revocation list.

A client secret inherits the identity of the API key that created it. The gateway resolves the source API key live on every request, so quota, routing, billing, logging, and safety policy always reflect the current state of that key.

## Token format

A client secret is the fixed prefix `csak-` followed by a compact JWS (JWT) signed with `RS256`. The prefix identifies the credential type (as in the previous scheme) and is stripped before JWT processing. The JWT header carries a `kid` identifying the signing key. Claims:

| Claim      | Meaning                                                        |
|------------|----------------------------------------------------------------|
| `iss`      | Fixed issuer identifier of the Bella OpenAPI deployment        |
| `aud`      | Audience identifier shared by the gateway and SPI services     |
| `sub`      | `ak_code` of the source API key                                |
| `clientid` | Caller-provided correlation identifier (1–128 characters)      |
| `iat`      | Issued-at time (epoch seconds)                                 |
| `nbf`      | Not valid before (equal to `iat`)                              |
| `exp`      | Expiry time (epoch seconds)                                    |
| `jti`      | Unique token id, used for revocation                           |

The JWT does not contain the full API key and does not contain a snapshot of the source key's authorized paths. Path-level authorization is enforced by the gateway at request time; SPI services authorize at audience granularity (see below).

Source API key permissions and status are otherwise snapshotted at issuance: disabling the source key or shrinking its permissions does not invalidate JWTs already held by pure-SPI services until they expire or are explicitly revoked. On the gateway itself, disabling the source key takes effect immediately because the key is resolved live.

## Create

Only a long-lived API key can create a client secret. The API key must have permission for `/v1/client_secrets`.

```bash
curl -X POST http://localhost:8080/v1/client_secrets \
  -H 'Authorization: Bearer <API_KEY>' \
  -H 'Content-Type: application/json' \
  -d '{"expires_in":600,"clientid":"app-client-123"}'
```

`expires_in` is measured in seconds, defaults to `600`, and must be between `60` and `28800` (8 hours). The TTL is controlled by the creator; there is no server-side renewal. `clientid` is a caller-provided correlation identifier; it is not an authenticated user identity or a billing subject.

The response contains the only returned copy of the token:

```json
{
  "client_secret": "csak-eyJhbGciOiJSUzI1NiIsImtpZCI6ImJlbGxhLTIwMjYtMDEifQ....",
  "clientid": "app-client-123",
  "expires_at": 1785946200
}
```

Creation responses include `Cache-Control: no-store` and `Pragma: no-cache`. Do not log, persist in browser storage, place in URLs, or expose the token to untrusted clients.

## Use with HTTP, uploads, and SSE

Use the same authorization header as an API key:

```http
Authorization: Bearer csak-eyJhbGciOiJSUzI1NiIs...
```

The existing `x-api-key` fallback is also accepted. The credential extractor distinguishes a client secret from a long-lived API key by the `csak-` prefix. Do not send a client secret in a query parameter, cookie, JSON business field, or multipart field.

On the gateway, each request verifies the JWT locally (signature, `exp`/`nbf` with 60 seconds of clock-skew leeway, `iss`, `aud`), checks the `jti` against the revocation list in Redis, then resolves the source API key by `sub` and enforces its current status and path permissions. SSE reconnects and new HTTP requests authenticate again.

## SPI-based services

Services using the SDK's default `BellaRequestFilter` support client secrets without adding endpoint-specific authentication code. The `openapi-spi` module provides the JWT machinery (`com.ke.bella.openapi.auth`): `ClientSecretProperties` binds the static key configuration (`bella.client-secret.*`), and `ClientSecretAuthenticator` verifies client secrets locally while delegating long-lived API keys to the wrapped `OpenapiClient`:

```java
new BellaRequestFilter(serviceId, new ClientSecretAuthenticator(clientSecretProperties, openapiClient));
```

There is no central fallback: a service without configured public keys rejects client secrets outright. Each request performs, entirely locally:

1. Parse the JWT and select the public key by `kid` from static configuration.
2. Verify the `RS256` signature.
3. Verify `iss` and `aud` against configured values.
4. Verify `exp` and `nbf` with 60 seconds of leeway.
5. Reject the token if its `jti` appears in the locally cached revocation list.

On success the verifier exposes `ak_code` (`sub`) and `clientid` through `BellaContext`. No network call to Bella OpenAPI is made on the request path, and there is no per-request Redis dependency.

**Authorization granularity.** Because the JWT carries no path snapshot, an SPI service cannot check locally whether the source API key is authorized for the specific request path. Possession of a valid, unrevoked token is sufficient to access any SPI service that accepts the configured audience — authorization granularity for SPI services is the audience, not the path. Path-level permissions are enforced only by the gateway itself.

**Revocation propagation.** The verifier maintains an in-memory set of revoked `jti` values, refreshed by polling the revocation list endpoint (recommended interval: 30–60 seconds). Revocation therefore takes effect on SPI services within one polling interval, not immediately. If polling fails, the verifier keeps its last successful snapshot and continues to verify signatures normally — revocation enforcement degrades open while authentication itself stays closed. Verifiers should log and alert when the snapshot becomes stale.

## Use with WebSocket

Non-browser clients should send the standard `Authorization` header during the WebSocket handshake.

Browser `WebSocket` APIs cannot set that header. Encode the token with unpadded Base64 URL encoding and pass it as a subprotocol named `bella-auth.<encoded>`:

```js
const token = "csak-eyJhbGciOiJSUzI1NiIs...";
const encoded = btoa(token).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
const socket = new WebSocket("wss://example.com/v1/audio/realtime?model=<MODEL>", `bella-auth.${encoded}`);
```

The credential subprotocol is consumed only for authentication and is not selected in the handshake response.

The JWT is verified at handshake time. When a new logical task starts on an existing connection, the handler locally re-checks `exp` and the cached revocation list. Expiration or revocation does not close the current WebSocket connection and does not interrupt an active task; it only rejects subsequent new tasks on that connection.

## Revoke

Use the source long-lived API key. The request presents the full token; the server verifies its signature, checks that `sub` matches the calling key's `ak_code`, and stores only the token's `jti` — the token itself is never persisted.

```bash
curl -X DELETE http://localhost:8080/v1/client_secrets \
  -H 'Authorization: Bearer <API_KEY>' \
  -H 'Content-Type: application/json' \
  -d '{"client_secret":"csak-eyJhbGciOiJSUzI1NiIs..."}'
```

Revocation is idempotent and does not echo the token. Revoking an already-expired or unknown-but-validly-signed token succeeds silently.

Revoked `jti` values are stored in a Redis sorted set (`bella:client-secret:revoked`, member = `jti`, score = revocation epoch seconds). Entries older than `28800` seconds (the maximum TTL) are trimmed, since any token revoked more than 8 hours ago has necessarily expired. The revocation list is the only central state in the client-secret system.

## List revoked tokens

SPI services poll this endpoint to refresh their local revocation cache. It requires a valid credential and returns only `jti` values.

```bash
curl 'http://localhost:8080/v1/client_secrets/revoked?since=1785942600' \
  -H 'Authorization: Bearer <API_KEY>'
```

```json
{
  "server_time": 1785946200,
  "revoked": [
    { "jti": "9b2f4a6e-...", "revoked_at": 1785943210 }
  ]
}
```

`since` is optional; when omitted, the full window (up to 8 hours) is returned. Pollers should pass the `server_time` of their previous successful response as `since` to fetch incrementally, and periodically perform a full pull to self-heal.

## Key management

- The private signing key is held only by Bella OpenAPI. Capability services are configured with static public keys.
- Configuration supports multiple public keys keyed by `kid`. Key rotation is: add the new public key to all verifiers, switch the issuer to the new private key, and remove the old public key after the last old-key token has expired (at most 8 hours). No protocol change is required.

## Failure behavior

- `client_secret_invalid` (`401`): malformed JWT, bad signature, unknown `kid`, wrong `iss`/`aud`, expired, not yet valid, or revoked.
- `client_secret_source_invalid` (`401`, gateway only): the source API key or its parent is no longer active.
- `client_secret_forbidden` (`403`, gateway only): the source API key no longer permits the requested path, or a client secret attempts to create or revoke another secret.

Redis is used only for the revocation list. If Redis is unavailable, the gateway skips the revocation check and continues to enforce signature, expiry, and live source-key status — revocation enforcement is temporarily degraded until Redis recovers, matching the polling-based semantics on SPI services. Unlike the previous Redis-backed scheme, Redis loss no longer invalidates outstanding client secrets, and issuing new secrets does not depend on Redis. SPI services never touch Redis; they depend only on the polling endpoint.

## Not supported

- Per-token usage-count limits.
- Immediate (sub-second) revocation on SPI services; revocation there is bounded by the polling interval.

If either becomes a hard requirement, it needs additional central state (a synchronous blacklist check or a token version scheme) and should be revisited explicitly.
