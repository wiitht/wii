package org.wii.dex.mesh.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.common.util.ReleasableHolder;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:20
 * @Version 1.0
 */
public class EventLoopScheduler {
    private static final long CLEANUP_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final List<EventLoop> eventLoops;
    private final Map<String, EventLoopScheduler.State> map = new ConcurrentHashMap<>();
    private int counter;
    private volatile long lastCleanupTimeNanos = System.nanoTime();

    EventLoopScheduler(EventLoopGroup eventLoopGroup) {
        eventLoops = Streams.stream(eventLoopGroup)
                .map(EventLoop.class::cast)
                .collect(toImmutableList());
    }

    EventLoopScheduler.Entry acquire(Endpoint endpoint) {
        requireNonNull(endpoint, "endpoint");
        final EventLoopScheduler.State state = state(endpoint);
        final EventLoopScheduler.Entry acquired = state.acquire();
        cleanup();
        return acquired;
    }

    @VisibleForTesting
    List<EventLoopScheduler.Entry> entries(Endpoint endpoint) {
        return state(endpoint).entries();
    }

    private EventLoopScheduler.State state(Endpoint endpoint) {
        final String authority = endpoint.authority();
        return map.computeIfAbsent(authority, e -> new EventLoopScheduler.State(eventLoops));
    }

    /**
     * Cleans up empty entries with no activity for more than 1 minute. For reduced overhead, we perform this
     * only when 1) the last clean-up was more than 1 minute ago and 2) the number of acquisitions % 256 is 0.
     */
    private void cleanup() {
        if ((++counter & 0xFF) != 0) { // (++counter % 256) != 0
            return;
        }

        final long currentTimeNanos = System.nanoTime();
        if (currentTimeNanos - lastCleanupTimeNanos < CLEANUP_INTERVAL_NANOS) {
            return;
        }

        for (final Iterator<EventLoopScheduler.State> i = map.values().iterator(); i.hasNext();) {
            final EventLoopScheduler.State state = i.next();
            final boolean remove;

            synchronized (state) {
                remove = state.allActiveRequests == 0 &&
                        currentTimeNanos - state.lastActivityTimeNanos >= CLEANUP_INTERVAL_NANOS;
            }

            if (remove) {
                i.remove();
            }
        }

        lastCleanupTimeNanos = System.nanoTime();
    }

    private static final class State {
        /**
         * A binary heap of Entry. Ordered by:
         * <ul>
         *   <li>{@link EventLoopScheduler.Entry#activeRequests()} (lower is better)</li>
         *   <li>{@link EventLoopScheduler.Entry#id()} (lower is better)</li>
         * </ul>
         */
        private final List<EventLoopScheduler.Entry> entries;
        private final List<EventLoop> eventLoops;
        private int nextUnusedEventLoopIdx;
        private int allActiveRequests;

        /**
         * Updated only when {@link #allActiveRequests} is 0 by {@link #release(EventLoopScheduler.Entry)}.
         */
        private long lastActivityTimeNanos = System.nanoTime();

        State(List<EventLoop> eventLoops) {
            this.eventLoops = eventLoops;
            entries = new ArrayList<>();
            nextUnusedEventLoopIdx = ThreadLocalRandom.current().nextInt(eventLoops.size());
            addUnusedEventLoop();
        }

        List<EventLoopScheduler.Entry> entries() {
            return entries;
        }

        synchronized EventLoopScheduler.Entry acquire() {
            EventLoopScheduler.Entry e = entries.get(0);
            if (e.activeRequests() > 0) {
                // All event loops are handling connections; try to add an unused event loop.
                if (addUnusedEventLoop()) {
                    e = entries.get(0);
                    assert e.activeRequests() == 0;
                }
            }

            assert e.index() == 0;
            e.activeRequests++;
            allActiveRequests++;
            bubbleDown(0);
            return e;
        }

        private boolean addUnusedEventLoop() {
            if (entries.size() < eventLoops.size()) {
                push(new EventLoopScheduler.Entry(this, eventLoops.get(nextUnusedEventLoopIdx), entries.size()));
                nextUnusedEventLoopIdx = (nextUnusedEventLoopIdx + 1) % eventLoops.size();
                return true;
            } else {
                return false;
            }
        }

        synchronized void release(EventLoopScheduler.Entry e) {
            assert e.parent() == this;
            e.activeRequests--;
            bubbleUp(e.index());
            if (--allActiveRequests == 0) {
                lastActivityTimeNanos = System.nanoTime();
            }
        }

        // Heap implementation, modified from the public domain code at https://stackoverflow.com/a/714873
        private void push(EventLoopScheduler.Entry e) {
            entries.add(e);
            bubbleUp(entries.size() - 1);
        }

        private void bubbleDown(int i) {
            int best = i;
            for (;;) {
                final int oldBest = best;
                final int left = left(best);

                if (left < entries.size()) {
                    final int right = right(best);
                    if (isBetter(left, best)) {
                        if (right < entries.size()) {
                            if (isBetter(right, left)) {
                                // Left leaf is better but right leaf is even better.
                                best = right;
                            } else {
                                // Left leaf is better than the current entry and right left.
                                best = left;
                            }
                        } else {
                            // Left leaf is better and there's no right leaf.
                            best = left;
                        }
                    } else if (right < entries.size()) {
                        if (isBetter(right, best)) {
                            // Left leaf is not better but right leaf is better.
                            best = right;
                        } else {
                            // Both left and right leaves are not better.
                            break;
                        }
                    } else {
                        // Left leaf is not better and there's no right leaf.
                        break;
                    }
                } else {
                    // There are no leaves, because right leaf can't be present if left leaf isn't.
                    break;
                }

                swap(best, oldBest);
            }
        }

        private void bubbleUp(int i) {
            while (i > 0) {
                final int parent = parent(i);
                if (isBetter(parent, i)) {
                    break;
                }

                swap(parent, i);
                i = parent;
            }
        }

        /**
         * Returns {@code true} if the entry at {@code a} is a better choice than the entry at {@code b}.
         */
        private boolean isBetter(int a, int b) {
            final EventLoopScheduler.Entry entryA = entries.get(a);
            final EventLoopScheduler.Entry entryB = entries.get(b);
            if (entryA.activeRequests() < entryB.activeRequests()) {
                return true;
            }
            if (entryA.activeRequests() > entryB.activeRequests()) {
                return false;
            }

            return entryA.id() < entryB.id();
        }

        private static int parent(int i) {
            return (i - 1) / 2;
        }

        private static int left(int i) {
            return 2 * i + 1;
        }

        private static int right(int i) {
            return 2 * i + 2;
        }

        private void swap(int i, int j) {
            final EventLoopScheduler.Entry entryI = entries.get(i);
            final EventLoopScheduler.Entry entryJ = entries.get(j);
            entries.set(i, entryJ);
            entries.set(j, entryI);

            // Swap the index as well.
            entryJ.setIndex(i);
            entryI.setIndex(j);
        }

        @Override
        public String toString() {
            return '[' + Joiner.on(", ").join(entries) + ']';
        }
    }

    static final class Entry implements ReleasableHolder<EventLoop> {
        private final EventLoopScheduler.State parent;
        private final EventLoop eventLoop;
        private final int id;
        private int activeRequests;

        /**
         * Index in the binary heap {@link EventLoopScheduler.State#entries}. Updated by {@link EventLoopScheduler.State#swap(int, int)} after
         * {@link #activeRequests} is updated by {@link EventLoopScheduler.State#acquire()} and {@link EventLoopScheduler.State#release(EventLoopScheduler.Entry)}.
         */
        private int index;

        Entry(EventLoopScheduler.State parent, EventLoop eventLoop, int id) {
            this.parent = parent;
            this.eventLoop = eventLoop;
            this.id = index = id;
        }

        @Override
        public EventLoop get() {
            return eventLoop;
        }

        EventLoopScheduler.State parent() {
            return parent;
        }

        int id() {
            return id;
        }

        int index() {
            return index;
        }

        void setIndex(int index) {
            this.index = index;
        }

        int activeRequests() {
            return activeRequests;
        }

        @Override
        public void release() {
            parent.release(this);
        }

        @Override
        public String toString() {
            return "(" + index + ", " + id + ", " + activeRequests + ')';
        }
    }
}
