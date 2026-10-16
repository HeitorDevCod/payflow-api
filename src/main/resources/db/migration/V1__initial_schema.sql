CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    document      VARCHAR(11)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE products (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120)   NOT NULL,
    description VARCHAR(500),
    price       NUMERIC(12, 2) NOT NULL,
    active      BOOLEAN        NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_products_price CHECK (price > 0)
);

CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    customer_id UUID           NOT NULL REFERENCES users (id),
    status      VARCHAR(30)    NOT NULL,
    total       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT         NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_id);

CREATE TABLE order_items (
    id         UUID PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID           NOT NULL REFERENCES products (id),
    name       VARCHAR(120)   NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity   INTEGER        NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0)
);

CREATE TABLE payments (
    id               UUID PRIMARY KEY,
    order_id         UUID           NOT NULL REFERENCES orders (id),
    customer_id      UUID           NOT NULL REFERENCES users (id),
    method           VARCHAR(20)    NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    status_detail    VARCHAR(80),
    amount           NUMERIC(12, 2) NOT NULL,
    installments     INTEGER        NOT NULL,
    idempotency_key  VARCHAR(64)    NOT NULL,
    provider_id      VARCHAR(40),
    pix_qr_code      TEXT,
    pix_qr_code_base64 TEXT,
    ticket_url       VARCHAR(500),
    barcode          VARCHAR(80),
    expires_at       TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT         NOT NULL,
    CONSTRAINT uk_payments_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT uk_payments_provider UNIQUE (provider_id)
);

CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_status ON payments (status);

CREATE TABLE subscriptions (
    id                UUID PRIMARY KEY,
    customer_id       UUID           NOT NULL REFERENCES users (id),
    reason            VARCHAR(120)   NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    frequency         INTEGER        NOT NULL,
    frequency_type    VARCHAR(10)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    provider_id       VARCHAR(60),
    next_payment_date TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT         NOT NULL,
    CONSTRAINT uk_subscriptions_provider UNIQUE (provider_id)
);

CREATE INDEX idx_subscriptions_customer ON subscriptions (customer_id);

CREATE TABLE webhook_events (
    id           UUID PRIMARY KEY,
    request_id   VARCHAR(80)  NOT NULL,
    topic        VARCHAR(60)  NOT NULL,
    resource_id  VARCHAR(60)  NOT NULL,
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_webhook_events_request UNIQUE (request_id)
);

CREATE TABLE payment_audit (
    id          UUID PRIMARY KEY,
    payment_id  UUID        NOT NULL REFERENCES payments (id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    source      VARCHAR(30) NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_audit_payment ON payment_audit (payment_id);
