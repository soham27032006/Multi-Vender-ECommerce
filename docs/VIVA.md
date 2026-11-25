# Viva questions and answers

Answers refer to the actual classes in this project.

## Design and OOP

**1. Explain the architecture of your project.**
Four layers: Console UI (`ui`) -> Service (`service`) -> DAO (`dao`) -> MySQL. The UI only reads input and prints. Services hold the business rules (splitting orders, commission, status rules). DAOs only run SQL. Each layer talks only to the one below, so changing the database code doesn't touch the menus.

**2. Why is `User` abstract?**
There is no plain "user" in the system, only a `Customer` or a `Vendor`. `User` holds the shared fields (id, name, email, phone) and declares `getRole()` as abstract, so every subclass must say what it is. `new User()` is impossible, which is correct.

**3. Where is polymorphism used?**
`Customer` and `Vendor` override `getRole()` and `display()`. `MainApp.findByEmail()` calls `user.display()` without knowing the type. `UserDAO.map()` returns a `User` that is really a `Customer` or `Vendor` depending on the `role` column. The menus all override `Menu.show()`.

**4. Why did you override `equals()` and `hashCode()`?**
Two `Product` objects loaded separately for the same row must count as the same product in a `Set` or as a `Map` key. Both methods use only the database `id`. If `equals` is overridden, `hashCode` must be too, otherwise `HashMap`/`HashSet` look in the wrong bucket.

**5. What does `Product implements Comparable` give you?**
A natural order by price, so `stream().sorted()` and `Collections.sort()` list cheapest first without extra code. Ties break on id so two different products with the same price are not treated as equal.

**6. Why the `Payable` interface?**
`Payment` (customer to platform) and `Payout` (platform to vendor) both move money and both have `getAmount()`, `getStatus()` and `pay()`. `TablePrinter.totalAmount(List<? extends Payable>)` can total either kind with one method.

**7. Why does a parent order have sub-orders?**
The customer pays once, but each vendor packs and ships separately. So status, commission, payout and returns live on the `SubOrder`. Returning vendor A's items doesn't affect vendor B.

## Exceptions

**8. Why are `OutOfStockException` and `PaymentFailedException` checked, but `InvalidInputException` unchecked?**
Out-of-stock and a declined payment are normal business events the caller must plan for, so the compiler forces `checkout()` callers to handle them. Bad input and "order not found" are validation errors; making them unchecked keeps every method signature clean, and `Menu.runSafely()` catches them in one place.

**9. Why wrap `SQLException` in `DataAccessException`?**
So the UI and services don't depend on JDBC types. The original `SQLException` is kept as the cause for debugging. It is unchecked because most DB errors can't be fixed at the call site.

**10. How does the app never crash on bad input?**
`ConsoleInput` loops until the value is valid (number, range, email, phone). Each menu action runs inside `Menu.runSafely()`, which catches each exception type and prints an `[ERROR]` message, then returns to the menu.

## JDBC

**11. Why `PreparedStatement` and not `Statement` with string concatenation?**
Values are sent separately from the SQL, so input like `' OR '1'='1` is treated as data, not SQL. That prevents SQL injection. It also handles quoting and types (`setDouble`, `setTimestamp`) and lets the DB reuse the query plan.

**12. Why one transaction for checkout?**
Checkout writes to 6 tables and lowers stock. With `setAutoCommit(false)`, either all rows are saved (`commit`) or none (`rollback`). Without it, a payment failure after the stock update would leave stock reduced for an order that was never paid. `ManualTestRunner` tests 2 and 3 prove the rollback.

**13. Why do some DAO methods take a `Connection` parameter?**
A transaction belongs to one connection. If each DAO method opened its own connection, the inserts would be committed separately and `rollback` could not undo them. The service opens the connection, passes it to every DAO call, then commits or rolls back.

**14. How does `decreaseStock` stop overselling at the database level?**
`UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?` checks and decreases in one atomic statement. If stock is too low, 0 rows are updated, `decreaseStock` returns false, and `checkout` throws `OutOfStockException` and rolls back. This works even for another program using the same DB.

**15. You got a MySQL deadlock while testing. Why, and how did you fix it?**
Inserting `order_items` takes a shared lock on the product row (foreign key check). Two checkouts did that, then both waited to `UPDATE` the same row, each blocking the other. Fix: decrease stock first (takes the exclusive lock up front), and always lock products in id order so two carts never lock A-then-B and B-then-A.

**16. What is metadata in JDBC?**
`DatabaseMetaData` describes the server and driver (MySQL version, driver name, transaction support). `ResultSetMetaData` describes the columns of a result (name, type, size, nullable). Both are printed by `DBInfo`.

## Concurrency

**17. What race condition does `InventoryService` prevent?**
Two customers both read "stock = 1", both pass the check, both buy: 2 sold, 1 existed. Check and take are two steps and another thread can run between them. `reserve()` is `synchronized`, so only one thread at a time does check + reserve. The admin "in-memory race" demo shows the unsafe version oversell and the synchronized version not.

**18. Why does the unsafe demo sometimes show final stock 0 even though it oversold?**
`stock -= qty` is read, subtract, write. Two threads can read the same value and both write the same result, losing one update. That hides the oversell, so the demo compares successful buys with the starting stock instead.

**19. Difference between `wait()` and `sleep()`?**
`wait()` releases the object's lock and sleeps until another thread calls `notify`/`notifyAll`; it must be called inside `synchronized`. `sleep()` just pauses the thread and keeps any locks it holds. `PayoutProcessor` uses `wait()` so the producer can add items while the consumer waits; `sleep(200)` only simulates a slow bank transfer.

**20. Why is `wait()` inside a `while` loop and not an `if`?**
After waking up, the condition may be false again (another thread took the slot first, or a spurious wakeup). The `while` re-checks before continuing.

**21. Why `notifyAll()` and not `notify()`?**
Both the producer and the consumer wait on the same queue object. `notify()` might wake the wrong kind of thread (a producer when only a consumer can make progress). `notifyAll()` wakes all and each re-checks its condition.

**22. Why `ExecutorService` instead of `new Thread()` for each customer?**
A fixed pool of 5 reuses 5 threads for any number of tasks. `Callable` returns a result and `Future.get()` waits for it, which plain `Runnable` can't do. `shutdown()` + `awaitTermination()` stops the pool cleanly.

**23. Why is the low-stock monitor thread a daemon, and why catch every exception inside it?**
A daemon thread doesn't keep the JVM alive, so the app can exit even if `stop()` is missed. If a task in `scheduleAtFixedRate` throws, the executor silently cancels all future runs, so `checkStock()` catches errors and skips one check instead.

## Streams and files

**24. Explain `groupingBy` in `OrderService.buildOrder()`.**
`items.stream().collect(groupingBy(item -> item.getProduct().getVendorId(), TreeMap::new, toList()))` turns the cart into a map of vendorId -> that vendor's items. Each entry becomes one `SubOrder`. `TreeMap` keeps vendors in id order. In `ReportService`, `groupingBy` + `summingDouble` gives revenue per vendor, and `partitioningBy` splits sub-orders into delivered / not delivered.

**25. How does the CSV import handle bad rows?**
`BufferedReader.readLine()` reads one line at a time. `parseProduct()` checks the column count, numbers, positive price, non-negative stock and that the vendor exists, and throws `InvalidInputException` with the reason. The loop catches it, prints the line number and reason, and continues. try-with-resources closes the file even on error. `data/products.csv` has 4 bad rows to demonstrate this.

## Extra questions to be ready for

- **Why `double` for money and not `BigDecimal`?** Syllabus requirement. Every result is rounded with `MoneyUtil.round()` so small floating-point errors don't add up.
- **What happens if payment fails?** `PaymentFailedException`, rollback, reservation released, cart kept for retry. Nothing is stored.
- **Why is `CartService.getCart()` synchronized?** The concurrency demo calls it from many threads; a plain `HashMap` can corrupt itself under concurrent writes (`ConcurrentHashMap` is not allowed).
- **Why commit and release under one lock (`InventoryService.commitAndRelease`)?** Otherwise, for a moment after commit, DB stock is already lower but the reservation is still counted, so units are subtracted twice and another customer is wrongly told "out of stock". Test 5 found this bug.
