#!/usr/bin/env python3
"""Burst/correctness test for bookingMaster.   pip install aiohttp
   python loadtest/burst_test.py --base https://booking-master.onrender.com --n 20000 --concurrency 2000
Exits non-zero if any invariant is violated."""
import argparse, asyncio, collections, random, sys, time, uuid
import aiohttp

ADMIN = {"X-Admin-Token": "admin"}
failures = []

def check(cond, msg):
    print(("  PASS  " if cond else "  FAIL  ") + msg)
    if not cond:
        failures.append(msg)

async def post(s, url, user, key, seats):
    try:
        async with s.post(url, json={"seats": seats},
                          headers={"Authorization": f"Bearer {user}", "Idempotency-Key": key}) as r:
            return r.status, await r.json(content_type=None)
    except Exception as e:  # connection-level failure counts as a failure
        return -1, str(e)

async def new_show(s, base, name, seats, limit):
    async with s.post(f"{base}/shows", headers=ADMIN, json={
            "name": name, "seats": seats, "price_paise": 25000, "per_user_limit": limit}) as r:
        assert r.status == 201, await r.text()
        return (await r.json())["id"]

async def show(s, base, sid):
    async with s.get(f"{base}/shows/{sid}") as r:
        return await r.json()

def no5xx(statuses):
    bad = [x for x in statuses if x == -1 or x >= 500]
    check(not bad, f"no 5xx / connection errors (saw {len(bad)})")

def consistent(v):
    check(v["available"] + v["held"] + v["confirmed"] == v["total_seats"],
          f"available+held+confirmed == total ({v['available']}+{v['held']}+{v['confirmed']}=={v['total_seats']})")

async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--n", type=int, default=20000)
    ap.add_argument("--concurrency", type=int, default=2000)
    a = ap.parse_args()
    conn = aiohttp.TCPConnector(limit=a.concurrency)
    timeout = aiohttp.ClientTimeout(total=300)
    async with aiohttp.ClientSession(connector=conn, timeout=timeout) as s:
        base = a.base

        print(f"\n[1] HOT SEAT: {a.n} different users, same seat A12")
        sid = await new_show(s, base, "hot-seat", [f"A{i}" for i in range(1, 101)], 4)
        t = time.time()
        res = await asyncio.gather(*[post(s, f"{base}/shows/{sid}/reserve", f"u{i}", str(uuid.uuid4()), ["A12"])
                                     for i in range(a.n)])
        c = collections.Counter(st for st, _ in res)
        print(f"  {dict(c)} in {time.time()-t:.1f}s ({a.n/(time.time()-t):.0f} req/s)")
        check(c[201] == 1, "exactly one 201")
        check(c[409] == a.n - 1, "everyone else got 409")
        no5xx(list(c.elements()))
        v = await show(s, base, sid); consistent(v)
        check(v["held"] == 1, "exactly 1 seat held")

        print("\n[2] SPREAD: 5000 users each pick a random single seat out of 1000")
        sid = await new_show(s, base, "spread", [f"S{i}" for i in range(1000)], 4)
        res = await asyncio.gather(*[post(s, f"{base}/shows/{sid}/reserve", f"u{i}", str(uuid.uuid4()),
                                          [f"S{random.randrange(1000)}"]) for i in range(5000)])
        c = collections.Counter(st for st, _ in res)
        print(f"  {dict(c)}")
        no5xx(list(c.elements()))
        v = await show(s, base, sid); consistent(v)
        check(v["held"] == c[201], f"held seats ({v['held']}) == number of 201s ({c[201]}) -> no double booking")

        print("\n[3] PER-USER LIMIT: one user, 12 concurrent requests for different seats, limit 4")
        sid = await new_show(s, base, "limit", [f"L{i}" for i in range(50)], 4)
        res = await asyncio.gather(*[post(s, f"{base}/shows/{sid}/reserve", "greedy", str(uuid.uuid4()), [f"L{i}"])
                                     for i in range(12)])
        c = collections.Counter(st for st, _ in res)
        print(f"  {dict(c)}")
        check(c[201] == 4 and c[409] == 8, "exactly 4 succeed, 8 rejected with 409")
        no5xx(list(c.elements()))

        print("\n[4] IDEMPOTENCY: same key + same seats, 50 concurrent")
        sid = await new_show(s, base, "idem", ["I1", "I2"], 4)
        res = await asyncio.gather(*[post(s, f"{base}/shows/{sid}/reserve", "alice", "same-key", ["I1"]) for _ in range(50)])
        c = collections.Counter(st for st, _ in res)
        ids = {b["id"] for st, b in res if st in (200, 201)}
        print(f"  {dict(c)}")
        check(c[201] == 1 and c[200] == 49, "one 201, forty-nine 200 replays")
        check(len(ids) == 1, "all responses carry the same reservation id")
        st, _ = await post(s, f"{base}/shows/{sid}/reserve", "alice", "same-key", ["I2"])
        check(st == 409, "same key + different seats -> 409")
        v = await show(s, base, sid)
        check(v["held"] == 1, "only one seat held despite 50 duplicate requests")

        print("\n[5] ALL-OR-NOTHING: 1500 users each request 2 random seats out of 20")
        sid = await new_show(s, base, "pairs", [f"P{i}" for i in range(20)], 4)
        reqs = [random.sample([f"P{i}" for i in range(20)], 2) for _ in range(1500)]
        res = await asyncio.gather(*[post(s, f"{base}/shows/{sid}/reserve", f"u{i}", str(uuid.uuid4()), r)
                                     for i, r in enumerate(reqs)])
        c = collections.Counter(st for st, _ in res)
        print(f"  {dict(c)}")
        no5xx(list(c.elements()))
        v = await show(s, base, sid); consistent(v)
        check(v["held"] == 2 * c[201], f"held ({v['held']}) == 2 x successes ({c[201]}) -> no partial holds")

    print("\nRESULT:", "ALL CHECKS PASSED" if not failures else f"{len(failures)} FAILED")
    sys.exit(1 if failures else 0)

asyncio.run(main())
