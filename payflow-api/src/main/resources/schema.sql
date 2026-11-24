-- PayFlow Database Schema
-- MySQL 8+ with InnoDB engine

-- ============================================================
-- Users table
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(100)    NOT NULL,
    role            ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Payments table
-- ============================================================
CREATE TABLE IF NOT EXISTS payments (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    payment_id                  VARCHAR(36)     NOT NULL,
    user_id                     BIGINT          NOT NULL,
    amount                      DECIMAL(19, 4)  NOT NULL,
    currency                    VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status                      ENUM('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED',
                                     'REFUND_PENDING', 'PARTIALLY_REFUNDED', 'REFUNDED')
                                                NOT NULL DEFAULT 'PENDING',
    description                 VARCHAR(500)    NULL,
    payment_method              VARCHAR(50)     NOT NULL,
    provider_transaction_id     VARCHAR(100)    NULL,
    failure_reason              VARCHAR(500)    NULL,
    metadata                    JSON            NULL,
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_payment_id (payment_id),
    INDEX idx_payments_user_id (user_id),
    INDEX idx_payments_status (status),
    INDEX idx_payments_created_at (created_at),

    CONSTRAINT fk_payments_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Idempotency keys table
-- ============================================================
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    idempotency_key         VARCHAR(64)     NOT NULL,
    user_id                 BIGINT          NOT NULL,
    request_fingerprint     VARCHAR(64)     NOT NULL,
    payment_id              BIGINT          NULL,
    response_status_code    INT             NULL,
    response_body           TEXT            NULL,
    status                  ENUM('PROCESSING', 'COMPLETED', 'FAILED')
                                            NOT NULL DEFAULT 'PROCESSING',
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at              TIMESTAMP       NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    INDEX idx_idempotency_user_key (user_id, idempotency_key),
    INDEX idx_idempotency_expires_at (expires_at),

    CONSTRAINT fk_idempotency_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_idempotency_payment_id
        FOREIGN KEY (payment_id) REFERENCES payments (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Refunds table
-- ============================================================
CREATE TABLE IF NOT EXISTS refunds (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    refund_id           VARCHAR(36)     NOT NULL,
    payment_id          BIGINT          NOT NULL,
    amount              DECIMAL(19, 4)  NOT NULL,
    reason              VARCHAR(500)    NULL,
    status              ENUM('PENDING', 'SUCCESS', 'FAILED')
                                        NOT NULL DEFAULT 'PENDING',
    provider_refund_id  VARCHAR(100)    NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_refunds_refund_id (refund_id),
    INDEX idx_refunds_payment_id (payment_id),

    CONSTRAINT fk_refunds_payment_id
        FOREIGN KEY (payment_id) REFERENCES payments (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Webhook events table
-- ============================================================
CREATE TABLE IF NOT EXISTS webhook_events (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    event_id            VARCHAR(36)     NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    payment_id          BIGINT          NULL,
    payload             JSON            NOT NULL,
    status              ENUM('PENDING', 'DELIVERED', 'FAILED')
                                        NOT NULL DEFAULT 'PENDING',
    attempts            INT             NOT NULL DEFAULT 0,
    max_attempts        INT             NOT NULL DEFAULT 5,
    next_retry_at       TIMESTAMP       NULL,
    last_attempted_at   TIMESTAMP       NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_events_event_id (event_id),
    INDEX idx_webhook_events_status (status),
    INDEX idx_webhook_events_next_retry_at (next_retry_at),

    CONSTRAINT fk_webhook_events_payment_id
        FOREIGN KEY (payment_id) REFERENCES payments (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
