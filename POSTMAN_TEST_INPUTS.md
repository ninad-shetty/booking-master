# Postman Test Inputs

## Environment Variables

Create a Postman environment with these variables:

| Variable | Local value | Render value |
|---|---|---|
| `base_url` | `http://localhost:8080` | Your Render service URL, such as `https://booking-master.onrender.com` |
| `admin_token` | `admin` | The value of `ADMIN_TOKEN` in Render |
| `user_id` | `alice` | Any test user ID |
| `show_id` | Set by the create-show request | Set by the create-show request |
| `reservation_id` | Set by the reserve request | Set by the reserve request |
| `idempotency_key` | A unique value per reservation | A unique value per reservation |

The API expects `Authorization: Bearer <user-id>`; it currently treats the bearer value as the user ID, not as a JWT.

## Requests

### Health

`GET {{base_url}}/health`

Expected: `200 OK`

### Create Show

`POST {{base_url}}/shows`

Headers:

```text
Content-Type: application/json
X-Admin-Token: {{admin_token}}
```

Body, raw JSON:

```json
{
  "name": "Concert",
  "seats": ["A1", "A2", "A3", "A4", "A5", "A6", "A12"],
  "price_paise": 25000,
  "per_user_limit": 4
}
```

Expected: `201 Created`. Save the returned `id` as the `show_id` environment variable. In the request's **Tests** tab:

```javascript
pm.test("show created", () => pm.response.to.have.status(201));
pm.environment.set("show_id", pm.response.json().id);
```

### List Shows

`GET {{base_url}}/shows`

Expected: `200 OK` with a JSON array. Each show includes `total_seats`, `available`, `held`, `confirmed`, and a finite `seats` array.

### Get One Show

`GET {{base_url}}/shows/{{show_id}}`

Expected: `200 OK`. Verify seat counts with this optional **Tests** script:

```javascript
const show = pm.response.json();
pm.test("seat counts add up", () => {
  pm.expect(show.available + show.held + show.confirmed).to.eql(show.total_seats);
});
```

### Reserve Seats

`POST {{base_url}}/shows/{{show_id}}/reserve`

Headers:

```text
Content-Type: application/json
Authorization: Bearer {{user_id}}
Idempotency-Key: {{idempotency_key}}
```

Body, raw JSON:

```json
{
  "seats": ["A12"]
}
```

Expected: `201 Created` for a new reservation, or `200 OK` when replaying the same idempotency key and request. Save `id` from the response as `reservation_id`:

```javascript
pm.test("reservation accepted or replayed", () => {
  pm.expect([200, 201]).to.include(pm.response.code);
});
pm.environment.set("reservation_id", pm.response.json().id);
```

### Confirm Reservation

`POST {{base_url}}/reservations/{{reservation_id}}/confirm`

Header:

```text
Authorization: Bearer {{user_id}}
```

Expected: `200 OK` with status `CONFIRMED`.

### Cancel Reservation

`POST {{base_url}}/reservations/{{reservation_id}}/cancel`

Header:

```text
Authorization: Bearer {{user_id}}
```

Expected: `200 OK` with status `CANCELLED`. Use a separate reservation if you also want to test confirmation; a confirmed/cancelled reservation cannot be reused as a fresh hold.

## Negative Inputs

Use a valid show ID and change one item at a time:

| Scenario | Input/change | Expected |
|---|---|---|
| Missing admin token | Omit `X-Admin-Token` on create-show | `403 Forbidden` |
| Invalid admin token | Set `X-Admin-Token: wrong` | `403 Forbidden` |
| Empty show seats | Create-show body has `"seats": []` | `400 Bad Request` |
| Missing price | Omit `price_paise` | `400 Bad Request` |
| Missing bearer header | Omit `Authorization` on reserve | `401 Unauthorized` |
| Empty reservation seats | Reserve body has `"seats": []` | `400 Bad Request` |
| Unknown seat | Reserve `"seats": ["NO-SUCH-SEAT"]` | `404 Not Found` |
| Seat already held | Reserve an already-held seat as another user | `409 Conflict` |
| Per-user limit | Make concurrent reservations beyond the show limit | `409 Conflict` |
| Idempotency conflict | Reuse a key with different seats or user | `409 Conflict` |

## Load-Test Scenario Inputs

These mirror the scenarios in `loadtest/burst_test.py`; they are reference inputs for manual Postman runs, not instructions to send thousands of requests from Postman.

| Scenario | Show name | Seat numbers | Price (paise) | Per-user limit | Requests |
|---|---|---|---:|---:|---|
| Hot seat | `hot-seat` | `A1` through `A100`; all users request `A12` | 25000 | 4 | 20,000 distinct users; expect one winner |
| Spread | `spread` | `S0` through `S999` | 25000 | 4 | 5,000 users each randomly request one seat |
| Per-user limit | `limit` | `L0` through `L49` | 25000 | 4 | One user makes 12 concurrent single-seat requests; expect 4 successes |
| Idempotency replay | `idem` | `I1`, `I2` | 25000 | 4 | 50 identical requests using key `same-key`; then reuse that key for `I2` |
| All-or-nothing pairs | `pairs` | `P0` through `P19` | 25000 | 4 | 1,500 users each request two distinct random seats |
