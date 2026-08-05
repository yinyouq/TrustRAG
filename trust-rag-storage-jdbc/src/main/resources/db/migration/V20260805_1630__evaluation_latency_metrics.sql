ALTER TABLE eval_judge_detail
    ADD COLUMN judge_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE eval_report
    ADD COLUMN avg_latency_with_judge_ms NUMERIC(12,3),
    ADD COLUMN p90_latency_with_judge_ms NUMERIC(12,3),
    ADD COLUMN p95_latency_ms NUMERIC(12,3),
    ADD COLUMN p95_latency_with_judge_ms NUMERIC(12,3),
    ADD COLUMN p99_latency_ms NUMERIC(12,3),
    ADD COLUMN p99_latency_with_judge_ms NUMERIC(12,3);

UPDATE eval_report
SET avg_latency_with_judge_ms = avg_latency_ms,
    p90_latency_with_judge_ms = p90_latency_ms
WHERE avg_latency_with_judge_ms IS NULL
   OR p90_latency_with_judge_ms IS NULL;
