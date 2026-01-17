package com.ruler.one.model;

/**
 * 重试策略（Retry Policy）。
 * <p>
 * 用于在节点执行失败时决定：
 * <ul>
 *   <li>最多重试几次（maxAttempts）</li>
 *   <li>每次重试间隔多久（backoffMillis + backoffMultiplier）</li>
 * </ul>
 * <p>
 * 在本项目中，该策略由 {@code DagEngine.executeWithRetry(...)} 使用：
 * 当节点执行抛异常时，如果还没达到最大次数，则等待一段时间后再次执行。
 * <p>
 * 退避时间使用“指数退避（exponential backoff）”计算：
 * <pre>
 * delay(attempt) = backoffMillis * (backoffMultiplier ^ (attempt - 1))
 * </pre>
 * 注意：attempt 从 1 开始。
 */
public class RetryPolicy {

    /**
     * 最大尝试次数（包含第一次执行）。
     * <p>
     * 例如：
     * <ul>
     *   <li>maxAttempts = 1：不重试，只执行 1 次</li>
     *   <li>maxAttempts = 3：最多执行 3 次（首次 1 次 + 重试 2 次）</li>
     * </ul>
     */
    private int maxAttempts = 3;          // 默认 3

    /**
     * 第一次重试的基础等待时间（毫秒）。
     * <p>
     * 对应公式中的 backoffMillis。
     * 当 attempt=1 时，等待时间就是 backoffMillis。
     */
    private long backoffMillis = 1000;    // 默认 1s

    /**
     * 退避倍率（指数退避的底数）。
     * <p>
     * 例如 backoffMillis=1000, backoffMultiplier=2 时：
     * <ul>
     *   <li>attempt=1 -> 1000ms</li>
     *   <li>attempt=2 -> 2000ms</li>
     *   <li>attempt=3 -> 4000ms</li>
     * </ul>
     */
    private double backoffMultiplier = 2; // 1s,2s,4s...

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public long getBackoffMillis() { return backoffMillis; }
    public void setBackoffMillis(long backoffMillis) { this.backoffMillis = backoffMillis; }

    public double getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }

    /**
     * 计算某次尝试（attempt）的退避等待时间。
     * <p>
     * 约定：attempt 从 1 开始；如果传入 attempt<=0，会按 1 处理。
     *
     * @param attempt 第几次尝试（从 1 开始）
     * @return 等待时间（毫秒）
     */
    public long backoffForAttempt(int attempt) {
        // attempt 从 1 开始
        double factor = Math.pow(backoffMultiplier, Math.max(0, attempt - 1));
        return (long) (backoffMillis * factor);
    }
}
