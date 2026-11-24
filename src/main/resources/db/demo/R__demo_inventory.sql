INSERT INTO product (sku, name, available)
VALUES ('SCANNER-01', 'Barcode scanner', 1),
       ('LABEL-01', 'Label roll', 20)
ON CONFLICT (sku) DO NOTHING;
