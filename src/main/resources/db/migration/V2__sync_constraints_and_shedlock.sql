-- =====================================================================
-- LedgerSaaS - Constraint Sync & ShedLock
-- V2__sync_constraints_and_shedlock.sql
--
-- Java enum siniflari ile veritabani check kisitlarini hizalar ve
-- dagitik zamanlayici kilitleri icin ShedLock tablosunu olusturur.
-- =====================================================================

-- ---------------------------------------------------------------------
-- subscriptions.status  ->  SubscriptionStatus enumu ile hizala
-- (ACTIVE, CANCELED, PAST_DUE, EXPIRED, TRIALING)
-- ---------------------------------------------------------------------
ALTER TABLE subscriptions
    DROP CONSTRAINT chk_subscriptions_status;

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_status
        CHECK (status IN ('ACTIVE', 'CANCELED', 'PAST_DUE', 'EXPIRED', 'TRIALING'));

-- ---------------------------------------------------------------------
-- invoices.status  ->  InvoiceStatus enumu ile hizala
-- (PAID, UNPAID, FAILED)
-- ---------------------------------------------------------------------
ALTER TABLE invoices
    DROP CONSTRAINT chk_invoices_status;

ALTER TABLE invoices
    ADD CONSTRAINT chk_invoices_status
        CHECK (status IN ('PAID', 'UNPAID', 'FAILED'));

-- ---------------------------------------------------------------------
-- shedlock: dagitik ortamda zamanlanmis gorevlerin ayni anda tek
-- instance tarafindan calistirilmasini garanti eden kilit tablosu
-- ---------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
