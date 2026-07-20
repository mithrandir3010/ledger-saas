-- =====================================================================
-- LedgerSaaS - Soft Delete & Financial Reporting Views
-- V3__soft_delete_and_financial_views.sql
--
-- Yasal finansal uyumluluk icin soft delete kolonlari ve
-- MRR / ARPU raporlama view'lari.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Soft delete kolonlari
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE invoices
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Listelemeler her zaman is_deleted = false filtresiyle calisacagi icin
-- partial index'ler sorgu maliyetini dusuk tutar.
CREATE INDEX idx_users_not_deleted ON users (id) WHERE is_deleted = FALSE;
CREATE INDEX idx_invoices_not_deleted ON invoices (id) WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------
-- view_mrr_metrics: Aylik Tekrarlayan Gelir (MRR)
--
-- En az bir PAID (ve silinmemis) faturasi olan ACTIVE abonelikler
-- uzerinden hesaplanir: MONTHLY planlar price, YEARLY planlar price/12
-- olarak normalize edilir. JPA tarafinda tekil satir esleyebilmek icin
-- sabit id kolonu icerir.
-- ---------------------------------------------------------------------
CREATE VIEW view_mrr_metrics AS
SELECT
    CAST(1 AS BIGINT)                             AS id,
    CAST(COALESCE(SUM(
        CASE p.billing_interval
            WHEN 'MONTHLY' THEN p.price
            WHEN 'YEARLY'  THEN p.price / 12.0
        END), 0) AS NUMERIC(12, 2))               AS total_mrr,
    COUNT(DISTINCT s.id)                          AS paying_subscription_count,
    NOW()                                         AS calculated_at
FROM subscriptions s
         JOIN plans p ON p.id = s.plan_id
WHERE s.status = 'ACTIVE'
  AND EXISTS (SELECT 1
              FROM invoices i
              WHERE i.subscription_id = s.id
                AND i.status = 'PAID'
                AND i.is_deleted = FALSE);

-- ---------------------------------------------------------------------
-- view_arpu_metrics: Aktif Kullanici Basina Ortalama Gelir (ARPU)
--
-- ARPU = MRR / aktif (silinmemis) odeme yapan kullanici sayisi.
-- ---------------------------------------------------------------------
CREATE VIEW view_arpu_metrics AS
SELECT
    CAST(1 AS BIGINT)                             AS id,
    CAST(COALESCE(SUM(
        CASE p.billing_interval
            WHEN 'MONTHLY' THEN p.price
            WHEN 'YEARLY'  THEN p.price / 12.0
        END) / NULLIF(COUNT(DISTINCT s.user_id), 0), 0)
        AS NUMERIC(12, 2))                        AS arpu,
    COUNT(DISTINCT s.user_id)                     AS active_user_count,
    NOW()                                         AS calculated_at
FROM subscriptions s
         JOIN plans p ON p.id = s.plan_id
         JOIN users u ON u.id = s.user_id AND u.is_deleted = FALSE
WHERE s.status = 'ACTIVE'
  AND EXISTS (SELECT 1
              FROM invoices i
              WHERE i.subscription_id = s.id
                AND i.status = 'PAID'
                AND i.is_deleted = FALSE);
