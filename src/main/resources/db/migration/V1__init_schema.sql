-- =====================================================================
-- LedgerSaaS - Initial Schema
-- V1__init_schema.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- plans
-- ---------------------------------------------------------------------
CREATE TABLE plans (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL,
    price            NUMERIC(10,2) NOT NULL,
    billing_interval VARCHAR(20)   NOT NULL,
    features         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_plans_name UNIQUE (name),
    CONSTRAINT chk_plans_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_plans_billing_interval CHECK (billing_interval IN ('MONTHLY', 'YEARLY'))
);

-- ---------------------------------------------------------------------
-- subscriptions
-- ---------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL,
    plan_id              BIGINT      NOT NULL,
    status               VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE RESTRICT,
    CONSTRAINT chk_subscriptions_status
        CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED', 'TRIALING')),
    CONSTRAINT chk_subscriptions_period
        CHECK (current_period_end > current_period_start)
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions (plan_id);
CREATE INDEX idx_subscriptions_status  ON subscriptions (status);

-- CRITICAL: Bir kullanıcının aynı anda birden fazla ACTIVE veya PAST_DUE
-- aboneliği olmasını veritabanı seviyesinde engeller (Unique Partial Index).
CREATE UNIQUE INDEX idx_unique_active_user_subscription
    ON subscriptions (user_id)
    WHERE status IN ('ACTIVE', 'PAST_DUE');

-- ---------------------------------------------------------------------
-- invoices
-- ---------------------------------------------------------------------
CREATE TABLE invoices (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    billing_date    TIMESTAMPTZ   NOT NULL,
    pdf_url         VARCHAR(512),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_invoices_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_invoices_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT chk_invoices_status
        CHECK (status IN ('DRAFT', 'OPEN', 'PAID', 'VOID', 'UNCOLLECTIBLE'))
);

CREATE INDEX idx_invoices_subscription_id ON invoices (subscription_id);
CREATE INDEX idx_invoices_user_id         ON invoices (user_id);
CREATE INDEX idx_invoices_status          ON invoices (status);
CREATE INDEX idx_invoices_billing_date    ON invoices (billing_date);
