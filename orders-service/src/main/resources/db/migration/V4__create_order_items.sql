
CREATE TABLE order_items(
    id UUID PRIMARY KEY,
    order_id UUID  REFERENCES orders(id) NOT NULL,
    product_id UUID NOT NULL REFERENCES products (id),
    unit_price NUMERIC(19,2) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);