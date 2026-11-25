# Manual test guide

Every expected result below was checked by running the app on a fresh database.
Do the tests **in this order**, because later tests use the order created earlier.

## Before you start

1. Reset the database so ids and stock match this guide. Double-click `reset-db.cmd`
   (or run it in a terminal) and type the MySQL password `root`.
2. Start the app: double-click `run.cmd`, or in a terminal:
   ```
   cd "d:\JAVA PROJECT"
   run.cmd
   ```
3. Type each value and press Enter. In the tables, `->` separates the inputs of one step.

Notes:
- Every 10 seconds a `LOW STOCK ALERT` may print in the middle of a menu
  (Mechanical Keyboard has 4, Limited Sneakers has 2, threshold is 5). That is the
  background thread working. Ignore it and keep typing.
- Payments fail randomly 10% of the time. Test 0 turns that off so checkout is predictable.

Starting data:

| Product id | Name | Price | Stock | Vendor |
|---|---|---|---|---|
| 1 | Laptop Stand | 1,199.00 | 20 | #1 TechHub (`ravi@techhub.in`) |
| 2 | Mechanical Keyboard | 3,499.00 | 4 | #1 TechHub |
| 3 | Hoodie | 1,499.00 | 12 | #2 StyleCo (`meena@styleco.in`) |
| 4 | Limited Sneakers | 5,999.00 | 2 | #2 StyleCo |

Customers: `arjun@mail.com`, `priya@mail.com`, `kiran@mail.com`.

---

## Test 0: switch off random payment failure (Admin)

| Step | Type | Expected |
|---|---|---|
| Main menu | `3` | ADMIN menu |
| Set failure % | `9` -> `0` | `[OK] Simulated payments now fail 0% of the time.` |
| Back | `0` | MAIN MENU |

## Test 1: login and browse (Customer)

| Step | Type | Expected |
|---|---|---|
| Customer | `1` -> `1` -> `arjun@mail.com` | `Welcome, [CUSTOMER] #3 Arjun Sharma ...` then CUSTOMER menu |
| Browse, cheapest first | `1` -> `1` | Order: Laptop Stand, Hoodie, Mechanical Keyboard, Limited Sneakers |
| Browse, filter category | `1` -> `4` -> `clothing` | Only Hoodie |
| Browse, max price | `1` -> `5` -> `2000` | Laptop Stand, Hoodie |

## Test 2: wrong input is handled (Customer menu)

| Step | Type | Expected |
|---|---|---|
| Letters in menu | `abc` | `'abc' is not a whole number. Try again.` |
| Number out of range | `9` | `Enter a number from 0 to 8.` |
| Product that doesn't exist | `2` -> `99` -> `2` | `[ERROR] No product with id #99` |
| Quantity 0 | `2` -> `1` -> `0` | `Enter a whole number greater than 0.` (asks again) |
| Then give 1 | `1` | `[OK] Added. Cart total now Rs.1,199.00` |

## Test 3: out of stock (Customer)

| Step | Type | Expected |
|---|---|---|
| 5 sneakers, only 2 exist | `2` -> `4` -> `5` | `[ERROR] Out of stock: Product #4: requested 5, only 2 in stock` |

## Test 4: cart and checkout split by vendor (Customer)

| Step | Type | Expected |
|---|---|---|
| 2 more Laptop Stands | `2` -> `1` -> `2` | `Cart total now Rs.3,597.00` (same product merges to qty 3) |
| 1 Hoodie | `2` -> `3` -> `1` | `Cart total now Rs.5,096.00` |
| View cart | `3` | Laptop Stand x3 = 3,597.00, Hoodie x1 = 1,499.00, TOTAL Rs.5,096.00 |
| Checkout | `6` -> `1` (UPI) -> `y` | `[OK] Order #1 placed and paid: Rs.5,096.00` |
| | | `Sub-order #1 vendor #1 PLACED subtotal Rs.3,597.00` |
| | | `Sub-order #2 vendor #2 PLACED subtotal Rs.1,499.00` |
| Track orders | `7` | ORDER #1 with both sub-orders PLACED |

What this proves: one cart -> one parent order -> one sub-order per vendor -> one payment.

## Test 5: can't return before delivery (Customer)

| Step | Type | Expected |
|---|---|---|
| Return sub-order 1 | `8` -> `1` -> `Damaged` | `[ERROR] Only delivered sub-orders can be returned (current: PLACED)` |
| Logout | `0` | MAIN MENU |

## Test 6: vendor status tracking (Vendor TechHub)

| Step | Type | Expected |
|---|---|---|
| Login | `2` -> `1` -> `ravi@techhub.in` | VENDOR: TechHub (#1) |
| My sub-orders | `4` | `Sub-order #1 ... PLACED subtotal Rs.3,597.00 commission Rs.359.70 you get Rs.3,237.30` |
| Skip steps (not allowed) | `5` -> `1` -> `3` (DELIVERED) | `[ERROR] Cannot change status from PLACED to DELIVERED` |
| Confirm | `5` -> `1` -> `1` | `[OK] Sub-order #1 is now CONFIRMED` |
| Ship | `5` -> `1` -> `2` | `[OK] Sub-order #1 is now SHIPPED` |
| Deliver | `5` -> `1` -> `3` | `[OK] Sub-order #1 is now DELIVERED` |
| Other vendor's sub-order | `5` -> `2` -> `1` | `[ERROR] Sub-order #2 does not belong to vendor #1` |
| My products | `1` | Laptop Stand stock **17** (20 - 3) |
| Logout | `0` | |

Commission check: 10% of 3,597.00 = 359.70, vendor gets 3,597.00 - 359.70 = 3,237.30.

## Test 7: second vendor delivers (Vendor StyleCo)

| Step | Type | Expected |
|---|---|---|
| Login | `2` -> `1` -> `meena@styleco.in` | VENDOR: StyleCo (#2) |
| Confirm, ship, deliver | `5` -> `2` -> `1`, then `5` -> `2` -> `2`, then `5` -> `2` -> `3` | `Sub-order #2 is now CONFIRMED` / `SHIPPED` / `DELIVERED` |
| Logout | `0` | |

## Test 8: pay vendors with producer-consumer threads (Admin)

| Step | Type | Expected |
|---|---|---|
| Admin, pay vendors | `3` -> `8` | `state of vendor-payout-thread = WAITING` |
| | | `PAID vendor #1 Rs.3237.30 (payout #1)` |
| | | `PAID vendor #2 Rs.1349.10 (payout #2)` |
| | | both threads end `TERMINATED` |
| Back | `0` | |
| Check as vendor | `2` -> `1` -> `ravi@techhub.in` -> `6` | payout #1 `SUCCESS`, `Received: Rs.3,237.30 | Pending: Rs.0.00` |
| Logout | `0` | |

## Test 9: return one vendor's part only (Customer)

| Step | Type | Expected |
|---|---|---|
| Login | `1` -> `1` -> `arjun@mail.com` | |
| Return sub-order 1 | `8` -> `1` -> `Damaged on arrival` | `[OK] Return #1 accepted. Rs.3,597.00 refunded for sub-order #1.` |
| Return it again | `8` -> `1` -> `Again` | `[ERROR] Only delivered sub-orders can be returned (current: RETURNED)` |
| Track orders | `7` | Sub-order #1 `RETURNED`, sub-order #2 still `DELIVERED` |
| Browse | `1` -> `1` | Laptop Stand stock back to **20** |
| Logout | `0` | |

What this proves: only vendor #1's part was refunded and restocked; vendor #2 is untouched.

## Test 10: payment failure rolls back (Admin + Customer)

| Step | Type | Expected |
|---|---|---|
| Admin: always fail | `3` -> `9` -> `100` -> `0` | `fail 100% of the time` |
| Customer login | `1` -> `1` -> `arjun@mail.com` | |
| Add Hoodie | `2` -> `3` -> `1` | `Cart total now Rs.1,499.00` |
| Checkout | `6` -> `1` -> `y` | `[ERROR] Payment failed: Bank declined payment of Rs.1499.00 via UPI. Your order was NOT placed; nothing was charged.` |
| View cart | `3` | Hoodie still in cart (kept for retry) |
| Track orders | `7` | still only ORDER #1, no new order |
| Remove Hoodie, logout | `5` -> `3`, then `0` | `[OK] Removed.` |
| Admin: back to 0% | `3` -> `9` -> `0` -> `0` | `fail 0% of the time` |

Note: MySQL used up order id 2 during the failed attempt even though it rolled back
(AUTO_INCREMENT numbers are never given back). So the next order is #3, not #2. This is normal.

## Test 11: vendor cancels, stock comes back (Customer Priya + Vendor TechHub)

| Step | Type | Expected |
|---|---|---|
| Priya buys keyboard | `1` -> `1` -> `priya@mail.com` -> `2` -> `2` -> `1` -> `6` -> `2` (Card) -> `y` -> `0` | `[OK] Order #3 placed and paid: Rs.3,499.00`, `Sub-order #4` |
| TechHub cancels | `2` -> `1` -> `ravi@techhub.in` -> `5` -> `4` -> `4` (CANCELLED) | `[OK] Sub-order #4 is now CANCELLED` |
| My products | `1` | Mechanical Keyboard stock back to **4** |
| My payouts | `6` | payout for sub-order #4 is `REFUNDED` |
| Logout | `0` | |

## Test 12: registration validation (Customer)

| Step | Type | Expected |
|---|---|---|
| Register | `1` -> `2` -> `Rahul` | asks Email |
| Bad email | `bad-email` | `Invalid email. Example: name@example.com` |
| Email already used | `arjun@mail.com` | `That email is already registered.` |
| Good email | `rahul@mail.com` | asks Phone |
| Bad phone | `12345` | `Phone must be 10 digits starting with 6-9.` |
| Good phone, address | `9123456789` -> `Delhi` | `[OK] Registered as customer #6` |
| Logout | `0` | |

## Test 13: admin features

| Step | Type | Expected |
|---|---|---|
| Admin | `3` | |
| Reports | `1` | Top sellers, revenue per vendor, commission, status counts, payouts. Returned and cancelled sub-orders are left out of revenue. |
| Race demo | `6` -> `3` -> `10` | NOT synchronized: `RACE CONDITION: ... Oversold by 2!` (number may vary), synchronized: `OK: sold exactly what was in stock` |
| Import CSV | `2` | 4 lines skipped (missing column, negative price, `ten`, vendor 99), `Import finished: 6 imported, 4 skipped.` |
| Export orders | `3` | `Exported ... sub-order row(s) to data/orders_export.csv` (open the file in Excel) |
| Export payouts | `4` | `Exported ... payout row(s) to data/payouts_export.csv` |
| DB info | `5` -> `products` | `Database : MySQL 8.0.46`, driver, and the 6 columns of `products` |
| List users | `10` | all users incl. Rahul |
| Exit | `0` -> `0` | `low-stock monitor stopped`, `Goodbye.` |

## Optional: real checkouts on many threads (Admin option 7)

Needs a product with low stock: `3` -> `7` -> `4` (Limited Sneakers) -> `1` -> `y`.
Every customer tries to buy 1 at the same time. Expected: as many orders succeed as there
were sneakers in stock, the rest get "out of stock", and `stock after` is 0, never negative.

## Automatic tests

`run-tests.cmd` runs 34 checks and should end with `RESULT: 34 passed, 0 failed`.

## Start again

Run `reset-db.cmd` any time to get back to the starting data.
