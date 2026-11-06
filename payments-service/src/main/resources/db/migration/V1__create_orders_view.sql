CREATE TABLE orders_view (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PAID','CANCELLED'))
);
