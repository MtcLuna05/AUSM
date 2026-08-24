package com.luna.ausm.impl.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * A small striped primitive set for render-thread reads and chunk-worker updates.
 */
public final class ConcurrentLongSet {
    private static final int STRIPE_COUNT = 32;
    private static final int STRIPE_MASK = STRIPE_COUNT - 1;

    private final LongOpenHashSet[] stripes = new LongOpenHashSet[STRIPE_COUNT];

    public ConcurrentLongSet() {
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new LongOpenHashSet();
        }
    }

    public boolean add(long value) {
        LongOpenHashSet stripe = stripe(value);
        synchronized (stripe) {
            return stripe.add(value);
        }
    }

    public boolean remove(long value) {
        LongOpenHashSet stripe = stripe(value);
        synchronized (stripe) {
            return stripe.remove(value);
        }
    }

    public boolean contains(long value) {
        LongOpenHashSet stripe = stripe(value);
        synchronized (stripe) {
            return stripe.contains(value);
        }
    }

    public void clear() {
        for (LongOpenHashSet stripe : stripes) {
            synchronized (stripe) {
                stripe.clear();
            }
        }
    }

    public boolean isEmpty() {
        for (LongOpenHashSet stripe : stripes) {
            synchronized (stripe) {
                if (!stripe.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public int size() {
        int size = 0;
        for (LongOpenHashSet stripe : stripes) {
            synchronized (stripe) {
                size += stripe.size();
            }
        }
        return size;
    }

    private LongOpenHashSet stripe(long value) {
        long mixed = value ^ (value >>> 33);
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return stripes[(int) mixed & STRIPE_MASK];
    }
}
