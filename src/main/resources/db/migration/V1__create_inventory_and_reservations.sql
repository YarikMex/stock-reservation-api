CREATE TABLE product (
    sku text PRIMARY KEY,
    name text NOT NULL,
    available integer NOT NULL CHECK (available >= 0)
);

CREATE TABLE reservation (
    id uuid PRIMARY KEY,
    sku text NOT NULL REFERENCES product (sku),
    quantity integer NOT NULL CHECK (quantity > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    cancelled_at timestamptz
);
