package org.wiitht.wii.dex.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import javax.annotation.CheckReturnValue;
import java.util.concurrent.TimeUnit;

/**
 * @Author tanghong
 * @Date 18-8-15-下午4:31
 * @Version 1.0
 */
public class KeepAliveEnforcer{
    @VisibleForTesting
    static final int MAX_PING_STRIKES = 2;
    @VisibleForTesting
    static final long IMPLICIT_PERMIT_TIME_NANOS = TimeUnit.HOURS.toNanos(2);

    private final boolean permitWithoutCalls;
    private final long minTimeNanos;
    private final KeepAliveEnforcer.Ticker ticker;
    private final long epoch;

    private long lastValidPingTime;
    private boolean hasOutstandingCalls;
    private int pingStrikes;

    public KeepAliveEnforcer(boolean permitWithoutCalls, long minTime, TimeUnit unit) {
        this(permitWithoutCalls, minTime, unit, KeepAliveEnforcer.SystemTicker.INSTANCE);
    }

    @VisibleForTesting
    KeepAliveEnforcer(boolean permitWithoutCalls, long minTime, TimeUnit unit, KeepAliveEnforcer.Ticker ticker) {
        Preconditions.checkArgument(minTime >= 0, "minTime must be non-negative");

        this.permitWithoutCalls = permitWithoutCalls;
        this.minTimeNanos = Math.min(unit.toNanos(minTime), IMPLICIT_PERMIT_TIME_NANOS);
        this.ticker = ticker;
        this.epoch = ticker.nanoTime();
        lastValidPingTime = epoch;
    }

    /** Returns {@code false} when client is misbehaving and should be disconnected. */
    @CheckReturnValue
    public boolean pingAcceptable() {
        long now = ticker.nanoTime();
        boolean valid;
        if (!hasOutstandingCalls && !permitWithoutCalls) {
            valid = compareNanos(lastValidPingTime + IMPLICIT_PERMIT_TIME_NANOS, now) <= 0;
        } else {
            valid = compareNanos(lastValidPingTime + minTimeNanos, now) <= 0;
        }
        if (!valid) {
            pingStrikes++;
            return !(pingStrikes > MAX_PING_STRIKES);
        } else {
            lastValidPingTime = now;
            return true;
        }
    }

    /**
     * Reset any counters because PINGs are allowed in response to something sent. Typically called
     * when sending HEADERS and DATA frames.
     */
    public void resetCounters() {
        lastValidPingTime = epoch;
        pingStrikes = 0;
    }

    /** There are outstanding RPCs on the transport. */
    public void onTransportActive() {
        hasOutstandingCalls = true;
    }

    /** There are no outstanding RPCs on the transport. */
    public void onTransportIdle() {
        hasOutstandingCalls = false;
    }

    /**
     * Positive when time1 is greater; negative when time2 is greater; 0 when equal. It is important
     * to use something like this instead of directly comparing nano times. See {@link
     * System#nanoTime}.
     */
    private static long compareNanos(long time1, long time2) {
        // Possibility of overflow/underflow is on purpose and necessary for correctness
        return time1 - time2;
    }

    @VisibleForTesting
    interface Ticker {
        long nanoTime();
    }

    @VisibleForTesting
    static class SystemTicker implements KeepAliveEnforcer.Ticker {
        public static final KeepAliveEnforcer.SystemTicker INSTANCE = new KeepAliveEnforcer.SystemTicker();

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }
}
