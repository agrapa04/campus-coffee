# Security reflection

Group members: <add each member's name and student ID here>

## Why one-approval-per-user was unenforceable before authentication

The self-approval check compared the review's author to a `user_id` the **client supplied** — on
`POST /api/reviews` (the author) and `PUT /api/reviews/{id}/approve?user_id=...` (the approver). A caller
could send any id, so the check guarded a value it did not control: a user could act as someone else, or
send a different id on each request. The approval count was also an anonymous integer that never recorded
*who* approved, so a repeat by the same user was undetectable. Both gaps close only once the acting user is
taken from an authenticated principal instead of the request, and each approval is recorded per
`(review, user)` behind a unique constraint.

## Trust problems that remain

- **Open registration enables Sybil accounts.** Anyone can create accounts without limit, so one person can
  register enough users to reach the approval quorum alone — identity is authenticated, but not vouched for.
- **Credentials are only safe over TLS.** HTTP Basic sends the password on every request and the JWT is a
  bearer token; on plain HTTP both are exposed, so the app must run behind TLS.
- **No token revocation or refresh.** A leaked JWT stays valid until it expires (15 minutes); there is no way
  to revoke it and no refresh flow.
- **`GET /api/users` exposes every email address** to any unauthenticated caller, because reads are public.
