# Multi-Vendor E-Commerce Marketplace Order Engine

A console application for a marketplace where many vendors sell through one store.
A customer's cart becomes **one parent order**, split into **one sub-order per vendor**,
paid with **one (simulated) payment**. The platform keeps a 10% commission and pays
each vendor the rest. Each vendor tracks and ships its own sub-order, and returns are
refunded per vendor.

Course: 25CSE0202. Plain Java 21 + JDBC + MySQL, no frameworks.

## Flow

```
Cart --> Parent Order --> Vendor Sub-Orders --> Single Payment (simulated)
     --> Commission + Vendor Payouts --> Sub-order status tracking
     --> Returns / Refunds per vendor
```

## Architecture

```
+------------------------------------------------------------+
|  UI layer (com.marketplace.ui)                             |
|  MainApp -> CustomerMenu / VendorMenu / AdminMenu          |
|  ConsoleInput (validated Scanner input), Menu.runSafely()  |
+-----------------------------+------------------------------+
                              | calls
+-----------------------------v------------------------------+
|  Service layer (com.marketplace.service)                   |
|  CartService  OrderService  PaymentService                 |
|  InventoryService  OrderTrackingService  ReturnService     |
|  ReportService  CsvService                                 |
|  Threads: ConcurrentOrderSimulator  PayoutProcessor        |
|           LowStockMonitor                                  |
+-----------------------------+------------------------------+
                              | calls (passes Connection for transactions)
+-----------------------------v------------------------------+
|  DAO layer (com.marketplace.dao), Repository<T, ID>        |
|  UserDAO ProductDAO OrderDAO PaymentDAO PayoutDAO ReturnDAO|
+-----------------------------+------------------------------+
                              | JDBC (PreparedStatement only)
+-----------------------------v------------------------------+
|  MySQL: marketplace_db (8 tables, see sql/schema.sql)      |
+------------------------------------------------------------+

model/      plain domain classes + enums (no DB code)
exception/  custom checked and unchecked exceptions
util/       DBConnection, DBInfo, InputValidator, MoneyUtil, TablePrinter, ThreadLog
```

## Requirements

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 or newer | `pom.xml` compiles with `release 21`, so only Java 21 features are allowed |
| Maven | 3.9+ | downloads `mysql-connector-j` |
| MySQL | 8.x | server running on `localhost:3306` |

Only dependency: `com.mysql:mysql-connector-j:8.4.0`.

## Setup

### 1. Create the database

From the project folder (asks for the MySQL root password):

```
mysql -u root -p < sql\schema.sql
mysql -u root -p < sql\seed.sql
```

Or open both files in MySQL Workbench and run them in that order.

`seed.sql` adds:

| Role | Email (used to log in) | Id |
|------|------------------------|----|
| Vendor TechHub | `ravi@techhub.in` | 1 |
| Vendor StyleCo | `meena@styleco.in` | 2 |
| Customer | `arjun@mail.com` | 3 |
| Customer | `priya@mail.com` | 4 |
| Customer | `kiran@mail.com` | 5 |

plus 4 products. To start again from empty: `DROP DATABASE marketplace_db;` then run both scripts again.

### 2. Configure the connection

Edit `src/main/resources/db.properties`:

```
db.url=jdbc:mysql://localhost:3306/marketplace_db
db.user=root
db.password=your_password
```

Do not publish this file with a real password.

### 3. Build and run

```
mvn -q compile
run.cmd
```

`run.cmd` compiles and starts `com.marketplace.ui.MainApp`. The manual command is:

```
java -cp "target\classes;%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar" com.marketplace.ui.MainApp
```

### 4. Run the tests

```
run-tests.cmd
```

`ManualTestRunner` (no JUnit) creates its own test users and products in the
database, runs 5 test groups (34 checks), prints PASS/FAIL, and deletes its test data.
Exit code is 0 only if every check passed.

## Features

**Customer**
- Browse products: sorted by price (both ways) or name, filtered by category, max price or in-stock
- Cart: add, change quantity, remove; stock is checked on every add
- Checkout: cart split by vendor, one payment (UPI / Card / Net Banking / COD), all in one DB transaction
- Track orders: every sub-order's status
- Request a return for a delivered sub-order; only that vendor's part is refunded

**Vendor**
- My products, add product, restock / change price (own products only)
- My sub-orders, update status: PLACED -> CONFIRMED -> SHIPPED -> DELIVERED (or CANCELLED before shipping)
- My payouts: gross, commission, net, status, totals received and pending

**Admin**
- Reports: top sellers, revenue per vendor, total commission, delivered vs not, order value statistics, payouts
- CSV: import `data/products.csv` (bad rows are reported and skipped), export orders and payouts
- Database metadata (DatabaseMetaData + ResultSetMetaData)
- Concurrency demos: in-memory race (unsafe vs synchronized), real checkouts on 5 threads
- Pay vendors for delivered sub-orders (producer-consumer threads)
- Set the simulated payment failure rate (default 10%)

**Background**
- Low-stock alert every 10 seconds (ScheduledExecutorService, daemon thread, stopped on exit)

## Business rules

- Commission is 10% of each vendor's subtotal; payout = subtotal - commission.
- All money is `double` rounded to 2 decimals (`MoneyUtil.round`).
- Payment failure rolls back the whole checkout: no order, no stock change, cart kept.
- Status can only move forward (`OrderStatus.canMoveTo`). RETURNED only through the return flow.
- Cancel and return both restock the items and mark the vendor payout REFUNDED, in one transaction.
- Payouts start PENDING and are paid by the admin "Pay vendors" action once the sub-order is DELIVERED.

## Syllabus topic -> file mapping

| Topic | Where |
|-------|-------|
| Classes, objects, constructors, `this` | `model/*` (e.g. `Product`, `SubOrder`) |
| Encapsulation | private fields + getters/setters in all `model/*` |
| Inheritance | `User` -> `Customer`, `Vendor`; `Menu` -> `MainApp`, `CustomerMenu`, `VendorMenu`, `AdminMenu` |
| Abstract class | `model/User`, `ui/Menu` |
| Polymorphism (overriding) | `getRole()`, `display()` in `Customer`/`Vendor`; `show()` in menus; `UserDAO.map()` returns Customer or Vendor |
| Interfaces | `model/Payable` (Payment, Payout), `dao/Repository<T, ID>`, `Menu.Action` |
| Enums with fields/methods | `OrderStatus.canMoveTo()`, `PaymentMode(label)`, `PaymentStatus`, `ReturnStatus` |
| `toString`, `equals`, `hashCode` | `User`, `Product`, `SubOrder`, `ParentOrder` |
| String, StringBuilder | `util/InputValidator` (String methods), `Cart.toString()`, `TablePrinter` |
| Wrapper classes, autoboxing | `Map<Integer, CartItem>` in `Cart`, `Integer.parseInt` in `ConsoleInput`, `CsvService` |
| Checked custom exceptions | `OutOfStockException`, `PaymentFailedException` |
| Unchecked custom exceptions | `InvalidInputException`, `OrderNotFoundException`, `DataAccessException` (wraps `SQLException`) |
| try-with-resources | every DAO method, `CsvService`, `MainApp` (Scanner) |
| Multi-catch, rethrow | `OrderService.checkout()`, `ui/Menu.runSafely()` |
| List, Map, Set, Queue | `Cart` (LinkedHashMap), `InventoryService` (HashMap), `CsvService` (Set), `PayoutProcessor` (Queue/LinkedList) |
| Iterator / for-each | `Cart.getTotal()`, DAO ResultSet loops |
| Comparable | `Product.compareTo()` (by price) |
| Comparator | `ReportService.topSellingProducts()` (`comparing().reversed().thenComparing()`), `CustomerMenu.browse()` |
| Generics, bounded wildcards | `Repository<T, ID>`, `saveAll(List<? extends T>)`, `TablePrinter.printTable(? extends T, ? super T)`, `totalAmount(List<? extends Payable>)`, `ConsoleInput.readEnum(<E extends Enum<E>>)` |
| Lambdas, method references | `CartService` (`Cart::new`), `OrderService` (`CartItem::getLineTotal`), menus (`this::checkout`) |
| Streams + Collectors | `ReportService` (groupingBy, summingDouble, partitioningBy, summarizingDouble, counting, toMap), `OrderService.buildOrder()` (groupingBy) |
| Optional | `Repository.findById()`, `UserDAO.findVendorById()`, `orElseThrow` in services |
| LocalDateTime | `ParentOrder`, `SubOrder`, `Payment`, `JdbcHelper` |
| Thread, Runnable, join | `PayoutProcessor.process()` |
| synchronized | `InventoryService`, `ConcurrentOrderSimulator.SharedStock.buySafe()`, `CartService.getCart()` |
| wait / notifyAll | `PayoutProcessor.BoundedPayoutQueue` |
| ExecutorService, Callable, Future | `ConcurrentOrderSimulator`, `ManualTestRunner.testConcurrencyStock()` |
| ScheduledExecutorService | `LowStockMonitor` |
| Thread states, ThreadFactory | `util/ThreadLog`, `util/NamedThreadFactory` |
| JDBC DriverManager | `util/DBConnection` |
| PreparedStatement, ResultSet | all `dao/*` |
| Transactions (commit/rollback) | `OrderService.checkout()`, `ReturnService`, `OrderTrackingService.cancel()`, `OrderDAO.save()` |
| Batch update, generated keys | `OrderDAO.saveSubOrder()`, `JdbcHelper.getGeneratedId()` |
| DatabaseMetaData, ResultSetMetaData | `util/DBInfo` |
| BufferedReader / BufferedWriter (CSV) | `service/CsvService` |

## Project layout

```
pom.xml
run.cmd, run-tests.cmd
sql/schema.sql, sql/seed.sql
data/products.csv                 sample import (has 4 deliberately bad rows)
src/main/resources/db.properties
src/main/java/com/marketplace/
    model/ exception/ dao/ service/ util/ ui/
src/test/java/com/marketplace/ManualTestRunner.java
docs/VIVA.md                      likely viva questions with answers
```
