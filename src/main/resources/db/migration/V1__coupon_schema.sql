CREATE SEQUENCE coupon_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE coupon_issue_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE coupons (
    id BIGINT PRIMARY KEY DEFAULT nextval('coupon_seq'),
    name VARCHAR(100) NOT NULL,
    total_stock INTEGER NOT NULL,
    issued_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT coupon_name_nonblank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT coupon_total_stock_nonnegative CHECK (total_stock >= 0),
    CONSTRAINT coupon_issued_count_nonnegative CHECK (issued_count >= 0),
    CONSTRAINT coupon_issued_not_over_total CHECK (issued_count <= total_stock)
);

CREATE TABLE coupon_issues (
    id BIGINT PRIMARY KEY DEFAULT nextval('coupon_issue_seq'),
    coupon_id BIGINT NOT NULL REFERENCES coupons(id),
    user_id VARCHAR(100) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT coupon_issue_user_nonblank CHECK (user_id ~ '[^[:space:]]'),
    CONSTRAINT coupon_issue_unique_user UNIQUE (coupon_id, user_id)
);
CREATE INDEX coupon_issues_coupon_idx ON coupon_issues (coupon_id);
