-- Marketplace Order Engine schema (MySQL 8)
CREATE DATABASE IF NOT EXISTS marketplace_db;
USE marketplace_db;

-- One table for the whole User hierarchy; 'role' decides Customer vs Vendor.
CREATE TABLE IF NOT EXISTS users (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(120) NOT NULL UNIQUE,
    phone           VARCHAR(15)  NOT NULL,
    role            VARCHAR(10)  NOT NULL,          -- CUSTOMER / VENDOR
    address         VARCHAR(255) NULL,              -- customers only
    shop_name       VARCHAR(100) NULL,              -- vendors only
    commission_rate DECIMAL(4,2) NULL               -- vendors only, e.g. 0.10
);

CREATE TABLE IF NOT EXISTS products (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    vendor_id INT            NOT NULL,
    name      VARCHAR(100)   NOT NULL,
    category  VARCHAR(50)    NOT NULL,
    price     DECIMAL(10,2)  NOT NULL,
    stock     INT            NOT NULL,
    CONSTRAINT chk_stock CHECK (stock >= 0),
    FOREIGN KEY (vendor_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS parent_orders (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT           NOT NULL,
    order_date   DATETIME      NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sub_orders (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    parent_order_id INT           NOT NULL,
    vendor_id       INT           NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    subtotal        DECIMAL(10,2) NOT NULL,
    commission      DECIMAL(10,2) NOT NULL,
    updated_at      DATETIME      NOT NULL,
    FOREIGN KEY (parent_order_id) REFERENCES parent_orders(id) ON DELETE CASCADE,
    FOREIGN KEY (vendor_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    sub_order_id INT           NOT NULL,
    product_id   INT           NOT NULL,
    product_name VARCHAR(100)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (sub_order_id) REFERENCES sub_orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS payments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    parent_order_id INT           NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    mode            VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    paid_at         DATETIME      NULL,
    FOREIGN KEY (parent_order_id) REFERENCES parent_orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payouts (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    vendor_id    INT           NOT NULL,
    sub_order_id INT           NOT NULL,
    gross_amount DECIMAL(10,2) NOT NULL,
    commission   DECIMAL(10,2) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    paid_at      DATETIME      NULL,
    FOREIGN KEY (vendor_id) REFERENCES users(id),
    FOREIGN KEY (sub_order_id) REFERENCES sub_orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS return_requests (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    sub_order_id  INT           NOT NULL,
    reason        VARCHAR(255)  NOT NULL,
    refund_amount DECIMAL(10,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    requested_at  DATETIME      NOT NULL,
    FOREIGN KEY (sub_order_id) REFERENCES sub_orders(id) ON DELETE CASCADE
);
