-- Sample data for the demo. Run after schema.sql.
-- Vendors get ids 1 and 2 so data/products.csv matches them.
USE marketplace_db;

INSERT INTO users (id, name, email, phone, role, address, shop_name, commission_rate) VALUES
 (1, 'Ravi Kumar',   'ravi@techhub.in',   '9876500001', 'VENDOR',   NULL, 'TechHub',  0.10),
 (2, 'Meena Iyer',   'meena@styleco.in',  '9876500002', 'VENDOR',   NULL, 'StyleCo',  0.10),
 (3, 'Arjun Sharma', 'arjun@mail.com',    '9876500003', 'CUSTOMER', '12 MG Road, Bengaluru', NULL, NULL),
 (4, 'Priya Nair',   'priya@mail.com',    '9876500004', 'CUSTOMER', '5 Anna Salai, Chennai', NULL, NULL),
 (5, 'Kiran Rao',    'kiran@mail.com',    '9876500005', 'CUSTOMER', '8 FC Road, Pune',       NULL, NULL);

INSERT INTO products (vendor_id, name, category, price, stock) VALUES
 (1, 'Laptop Stand',       'Electronics', 1199.00, 20),
 (1, 'Mechanical Keyboard','Electronics', 3499.00,  4),
 (2, 'Hoodie',             'Clothing',    1499.00, 12),
 (2, 'Limited Sneakers',   'Footwear',    5999.00,  2);
