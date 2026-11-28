-- V13: Retail engine - suppliers, discounts, batches, stock movements, sales, refunds, purchase orders.
-- Money settles through the Pocket Money append-only ledger; this migration adds no cash ledger.

CREATE TABLE retail_suppliers (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES tenants(id),
    code       VARCHAR(60) NOT NULL,
    name       VARCHAR(150) NOT NULL,
    phone      VARCHAR(40),
    email      VARCHAR(150),
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version    BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_suppliers_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE retail_discounts (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES tenants(id),
    code       VARCHAR(60) NOT NULL,
    kind       VARCHAR(20) NOT NULL,
    discount_value NUMERIC(14,2) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ,
    version    BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retail_discounts_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_retail_discounts_kind CHECK (kind IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_retail_discounts_value CHECK (discount_value >= 0)
);

-- Generic write idempotency for retail commands that do not have a natural
-- idempotency column. The result reference is filled when the command commits.
CREATE TABLE retail_write_idempotency (
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    scope           VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    result_ref      UUID,
    result_json     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, scope, idempotency_key),
    CONSTRAINT ck_retail_write_idempotency_scope CHECK (scope <> '')
);

CREATE TABLE retail_batches (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    product_id         UUID NOT NULL REFERENCES store_items(id),
    batch_number       VARCHAR(80) NOT NULL,
    quantity           INTEGER NOT NULL,
    quantity_remaining INTEGER NOT NULL,
    unit_cost          NUMERIC(14,2) NOT NULL DEFAULT 0,
    expiry_date        DATE,
    received_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    supplier_id        UUID REFERENCES retail_suppliers(id),
    source             VARCHAR(40) NOT NULL,
    reference          VARCHAR(120),
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_retail_batches_tenant_product_batch UNIQUE (tenant_id, product_id, batch_number),
    CONSTRAINT ck_retail_batches_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_retail_batches_remaining CHECK (quantity_remaining >= 0 AND quantity_remaining <= quantity)
);

CREATE INDEX idx_retail_batches_product ON retail_batches(tenant_id, product_id);
CREATE INDEX idx_retail_batches_expiry ON retail_batches(tenant_id, expiry_date);

CREATE TABLE retail_stock_movements (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    product_id     UUID NOT NULL REFERENCES store_items(id),
    type           VARCHAR(20) NOT NULL,
    quantity       INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    reference      VARCHAR(120),
    reason         VARCHAR(255),
    batch_id       UUID REFERENCES retail_batches(id),
    performed_by   UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT ck_retail_stock_movements_type CHECK (type IN ('RESTOCK', 'SALE', 'RETURN', 'WRITE_OFF', 'ADJUST'))
);

CREATE INDEX idx_retail_stock_movements_product ON retail_stock_movements(tenant_id, product_id, created_at DESC);
CREATE INDEX idx_retail_stock_movements_batch ON retail_stock_movements(batch_id);

CREATE TABLE retail_sales (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    reference            VARCHAR(60) NOT NULL,
    student_id           UUID NOT NULL,
    wallet_id            UUID NOT NULL,
    card_id              UUID,
    merchant_id          UUID NOT NULL REFERENCES pm_merchants(id),
    terminal_id          UUID NOT NULL REFERENCES pm_terminals(id),
    subtotal             BIGINT NOT NULL,
    discount             BIGINT NOT NULL DEFAULT 0,
    total                BIGINT NOT NULL,
    status               VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    payment_method       VARCHAR(30) NOT NULL DEFAULT 'POCKET_MONEY',
    pocket_purchase_id   UUID,
    pricing_fingerprint  VARCHAR(64) NOT NULL,
    idempotency_key      VARCHAR(120) NOT NULL,
    request_hash         VARCHAR(64) NOT NULL,
    receipt_no           VARCHAR(60) NOT NULL,
    notes                VARCHAR(500),
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT uq_retail_sales_tenant_reference UNIQUE (tenant_id, reference),
    CONSTRAINT uq_retail_sales_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_retail_sales_tenant_receipt UNIQUE (tenant_id, receipt_no),
    CONSTRAINT ck_retail_sales_amounts CHECK (
        subtotal >= 0 AND discount >= 0 AND total >= 0
        AND discount <= subtotal AND total = subtotal - discount
    )
);

CREATE INDEX idx_retail_sales_student ON retail_sales(tenant_id, student_id, created_at DESC);

CREATE TABLE retail_sale_items (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    sale_id      UUID NOT NULL REFERENCES retail_sales(id) ON DELETE CASCADE,
    product_id   UUID NOT NULL REFERENCES store_items(id),
    sku          VARCHAR(60) NOT NULL,
    name         VARCHAR(150) NOT NULL,
    quantity     INTEGER NOT NULL,
    unit_price   BIGINT NOT NULL,
    line_total   BIGINT NOT NULL,
    unit_cost    BIGINT NOT NULL DEFAULT 0,
    price_source VARCHAR(30) NOT NULL DEFAULT 'CATALOG',
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT ck_retail_sale_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_retail_sale_items_amounts CHECK (
        unit_price >= 0 AND line_total >= 0 AND unit_cost >= 0
        AND line_total = unit_price * quantity
    )
);

CREATE INDEX idx_retail_sale_items_sale ON retail_sale_items(sale_id);

CREATE TABLE retail_sale_item_batches (
    sale_item_id UUID NOT NULL REFERENCES retail_sale_items(id) ON DELETE CASCADE,
    batch_id     UUID NOT NULL REFERENCES retail_batches(id),
    quantity     INTEGER NOT NULL,
    PRIMARY KEY (sale_item_id, batch_id),
    CONSTRAINT ck_retail_sale_item_batches_quantity CHECK (quantity > 0)
);

CREATE TABLE retail_refunds (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    sale_id         UUID NOT NULL REFERENCES retail_sales(id),
    amount          BIGINT NOT NULL,
    reason          VARCHAR(255),
    status          VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_retail_refunds_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_retail_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_retail_refunds_status CHECK (status IN ('COMPLETED', 'FAILED'))
);

CREATE INDEX idx_retail_refunds_sale ON retail_refunds(tenant_id, sale_id);

-- Tracks which allocations a refund consumed, so a partial return cannot
-- restock the same sale batch twice.
CREATE TABLE retail_refund_items (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    refund_id    UUID NOT NULL REFERENCES retail_refunds(id) ON DELETE CASCADE,
    sale_id      UUID NOT NULL REFERENCES retail_sales(id),
    sale_item_id UUID NOT NULL REFERENCES retail_sale_items(id),
    batch_id     UUID NOT NULL REFERENCES retail_batches(id),
    quantity     INTEGER NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT ck_retail_refund_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_retail_refund_items_sale_item ON retail_refund_items(tenant_id, sale_item_id);

CREATE TABLE retail_purchase_orders (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    reference   VARCHAR(60) NOT NULL,
    supplier_id UUID NOT NULL REFERENCES retail_suppliers(id),
    status      VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    total       NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes       VARCHAR(500),
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT uq_retail_purchase_orders_tenant_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_retail_purchase_orders_status CHECK (status IN ('OPEN', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);

CREATE INDEX idx_retail_purchase_orders_supplier ON retail_purchase_orders(tenant_id, supplier_id);

CREATE TABLE retail_purchase_order_items (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    order_id          UUID NOT NULL REFERENCES retail_purchase_orders(id) ON DELETE CASCADE,
    product_id        UUID NOT NULL REFERENCES store_items(id),
    quantity          INTEGER NOT NULL,
    quantity_received INTEGER NOT NULL DEFAULT 0,
    unit_cost         NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total        NUMERIC(14,2) NOT NULL DEFAULT 0,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_retail_po_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_retail_po_items_received CHECK (quantity_received >= 0 AND quantity_received <= quantity),
    CONSTRAINT ck_retail_po_items_line_total CHECK (line_total = unit_cost * quantity)
);

CREATE INDEX idx_retail_po_items_order ON retail_purchase_order_items(order_id);

-- Append-only protection: stock movements and refunds must never be mutated.
CREATE OR REPLACE FUNCTION retail_reject_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'retail append-only table % does not allow %', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_retail_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON retail_stock_movements
    FOR EACH ROW EXECUTE FUNCTION retail_reject_mutation();

CREATE TRIGGER trg_retail_refunds_append_only
    BEFORE UPDATE OR DELETE ON retail_refunds
    FOR EACH ROW EXECUTE FUNCTION retail_reject_mutation();
