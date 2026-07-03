# Account Read — Phase 1

We are exposing the read side of accounts for `CUSTOMER`: list my accounts and get one by `accountNumber`. Creation, update, lock/unlock, and balance mutation are deferred to later phases. The two decisions below settle the non-obvious parts of this slice.

## Decision 1: Ownership check lives in the repository, not the service

`AccountService` accepts `userId` (UUID) from the caller, resolves the owning `CustomerProfile`, and delegates to one of two ownership-checked repository methods:

- `findByCustomerProfileId(UUID customerProfileId)` — list
- `findByAccountNumberAndCustomerProfileId(String accountNumber, UUID customerProfileId)` — detail

Both methods have the customer profile id baked into the WHERE clause, so a Customer literally cannot observe another Customer's account, regardless of what `userId` the controller passes. The service never applies an in-memory filter on top of a "list all" query.

**Why.** Ownership is the only authorization rule for these endpoints. Pushing it into the query means the only code path that can return account rows is one that has already proven ownership — there is no "list all accounts and then check" branch to forget. The downside (more methods on the repository) is paid once and reviewed at the boundary that actually touches the database.

**Trade-off accepted.** The service cannot easily answer cross-customer questions like "all accounts across the bank" — that is a Teller / Admin concern with its own endpoints and its own queries. Read-side for Customer is intentionally narrow.

## Decision 2: One `AccountResponse` DTO, raw `BigDecimal` balance

A single record `AccountResponse` is used for both the list and detail endpoints, with fields: `accountNumber`, `accountType`, `currency`, `balance`, `status`, `createdAt`. `balance` is the raw `BigDecimal` (scale 4) straight from the entity — no rounding, no `displayBalance` twin, no per-endpoint reshape.

**Why.** Balance is the headline value of an account; hiding it from the list response to save bytes buys nothing for a customer with 0..1 account. Splitting `AccountSummary` / `AccountDetail` is the kind of premature distinction that creates two types to maintain and forces the client to maintain two parsing paths for what is the same resource. We can split later, backward-compatibly, if real pagination or a card-only list emerges.

Raw `BigDecimal` (scale 4) keeps every decimal the entity accepted. Banking precision does not get destroyed at the serialization boundary. The client renders; the server never throws away data.

**Trade-off accepted.** A naive frontend that JSON-parses `balance` as a JS `number` will lose precision past 2^53 cents. That is a client concern, not a server one — Jackson serializes BigDecimal as a JSON number, and any client that needs exact arithmetic parses it as a string and hands it to a decimal library. The MVP web app will use a decimal library; we will revisit only if a future client cannot do that.
