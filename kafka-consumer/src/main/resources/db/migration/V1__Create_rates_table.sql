-- Create the rates table
CREATE TABLE rates (
    id BIGSERIAL PRIMARY KEY,
    rate_name VARCHAR(255) NOT NULL,
    bid NUMERIC(19, 8),
    ask NUMERIC(19, 8),
    rate_updatetime TIMESTAMP,
    db_updatetime TIMESTAMP,
    pipeline_id VARCHAR(255),
    CONSTRAINT uq_rate_name_updatetime UNIQUE (rate_name, rate_updatetime)
);

-- Optional: Add indexes for performance if needed, e.g., on rate_name or rate_updatetime individually
-- CREATE INDEX IF NOT EXISTS idx_rates_rate_name ON rates (rate_name);
-- CREATE INDEX IF NOT EXISTS idx_rates_rate_updatetime ON rates (rate_updatetime);
