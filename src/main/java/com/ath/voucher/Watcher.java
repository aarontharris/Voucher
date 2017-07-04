package com.ath.voucher;

import android.os.SystemClock;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Spins up a single thread that begins to poll at 100ms.<br>
 * The thread is created on-demand.<br>
 * The thread is kept alive only until all watchers are satisfied and then it dies.<br>
 * Only 1 thread is used, regardless of the number of watchers.<br>
 * <br>
 * WARN: Not supremely accurate.<br>
 * Precision will be approximately within 100ms of your requested timeout<br>
 * <br>
 * WARN: Polling will continue even if the app is in the background as long as watchers are active.<br>
 * If that is not what you want, you may want to cancel your watchers.<br>
 * Generally if you keep your timeouts reasonable, the polling will kill itself once all the watchers timeout.<br>
 * HOWEVER: if you override {@link #getNowMillis()} to observe Deep Sleep, the Watchers may not timeout while in deep sleep, so be careful.<br>
 * <br>
 * Override {@link #onTimeExceeded()} to get notified when the timeout occurs.<br>
 * Call {@link #start()} to begin watching.<br>
 * Call {@link #cancel()} if you no longer need to be notified.<br>
 */
public abstract class Watcher {

    private long mWatchStartMillis = Long.MAX_VALUE;
    private long mDelayMillis = 0;
    private boolean mEnabled = false;

    public Watcher(long timeoutMillis) {
        mDelayMillis = timeoutMillis;
    }

    /**
     * How the system obtains the current time in millis.<br>
     * You may override this if you wish to use another clock.<br>
     * Recommended if you want to observe sleep mode, IE: override and return {@link SystemClock#uptimeMillis()}
     *
     * @return the current milliseconds that have passed (used for timing).
     */
    public long getNowMillis() {
        return System.currentTimeMillis();
    }

    public final long getElapsed() {
        long delta = (getNowMillis() - mWatchStartMillis);
        return delta;
    }

    public final boolean isTimeExceeded() {
        return getElapsed() >= mDelayMillis;
    }

    public final Watcher start() {
        mWatchStartMillis = getNowMillis();
        mEnabled = true;
        WatcherManager.get().addTask(this);
        return this;
    }

    public final void cancel() {
        mEnabled = false;
        WatcherManager.get().cancelTask(this);
    }

    public final boolean isEnabled() {
        return mEnabled;
    }

    /**
     * WARN: only to be called by the poller from within the locked area
     */
    private final void notifyTimeExceeded() {
        if (isEnabled()) {
            cancel();
            onTimeExceeded();
        }
    }

    /**
     * Called from a background thread.<br>
     * You have very limited time, be quick!<br>
     */
    protected abstract void onTimeExceeded();

    private static final class WatcherManager {

        private static final WatcherManager self = new WatcherManager();
        private ReentrantLock mLock = new ReentrantLock();
        private ReentrantLock mPollerLock = new ReentrantLock();
        private ConcurrentLinkedQueue<Watcher> mWatchers = new ConcurrentLinkedQueue<>();
        private long mPollingIntervalMillis = 100;
        private WatcherPoller mPoller;

        private WatcherManager() {
        }

        private static final WatcherManager get() {
            return self;
        }

        private void startPolling() {
            mPollerLock.lock();
            Log.t("start polling got lock");
            try {
                if (mPoller != null) {
                    stopPolling();
                }
                mPoller = new WatcherPoller();
                Log.t("started polling");
                mPoller.start();
            } finally {
                Log.t("started polling unlocking");
                mPollerLock.unlock();
            }
        }

        private void stopPolling() {
            mPollerLock.lock();
            Log.t("stop polling got lock");
            try {
                if (mPoller != null) {
                    Log.t("stopped polling");
                    mPoller.stop();
                }
            } finally {
                Log.t("stopped polling unlocking");
                mPollerLock.unlock();
            }
        }

        void addTask(Watcher watcher) {
            mLock.lock();
            Log.t("addTask got the lock");
            try {
                Log.t("addTask got the lock - adding");
                mWatchers.add(watcher);
                if (mWatchers.size() == 1) {
                    Log.t("addTask got the lock - adding - starting");
                    startPolling();
                }
            } finally {
                Log.t("addTask released the lock");
                mLock.unlock();
            }
        }

        void cancelTask(Watcher watcher) {
            mLock.lock();
            Log.t("cancelTask got the lock");
            try {
                Log.t("cancelTask got the lock - canceling");
                mWatchers.remove(watcher);
                if (mWatchers.size() == 0) {
                    Log.t("cancelTask got the lock - canceling - stopping");
                    stopPolling();
                }
            } finally {
                Log.t("cancelTask releasing the lock");
                mLock.unlock();
            }
        }

        /* inner class */
        class WatcherPoller {
            private AtomicBoolean mRunning = new AtomicBoolean(false);

            void start() {
                if (!mRunning.getAndSet(true)) {
                    Log.t("start polling");
                    new Thread(new Runnable() {

                        @Override
                        public void run() {
                            while (mRunning.get()) {
                                mLock.lock();
                                try {
                                    if (mWatchers.size() == 0) {
                                        stop();
                                    } else {
                                        for (Watcher watcher : mWatchers) {
                                            if (watcher.isEnabled() && watcher.isTimeExceeded()) {
                                                watcher.notifyTimeExceeded();
                                            }
                                        }
                                    }
                                } finally {
                                    mLock.unlock();
                                }
                                try {
                                    // in case of concurrent modification of mRunning
                                    // no point in sleeping if we're not running
                                    if (mRunning.get()) {
                                        Thread.sleep(mPollingIntervalMillis);
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).start();
                }
            }

            void stop() {
                mRunning.set(false);
            }
        }
    }

}
