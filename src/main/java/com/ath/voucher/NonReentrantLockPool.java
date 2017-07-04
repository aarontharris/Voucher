package com.ath.voucher;


import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <pre>
 * Pools locks by key.
 *
 * This performs a lock two ways
 * 1. Traditional ReentrantLock to protect against calls from multiple threads.
 * 2. Less traditional, we use a simple boolean to protect against reentrancy of the same thread.
 * Why #2?  If the consumer is a dispatcher we want to not perform the same work twice so we must
 * lock until the work is complete, not just when the requesting thread is finished dispatching.
 *
 * For example: Imagine we only employed #1.
 * Thread 1 enters and attains the lock and locks it, spawns a thread to do the work and returns while work is still being performed.
 * (Thread 1 has not yet released the lock)
 * Thread 1 enters again a millisecond later from another consumer, because it is the same thread, reentrancy is allowed and causes the
 * work to be performed again.
 * </pre>
 */
public class NonReentrantLockPool {

    private static class LockState {
        private AtomicBoolean busy = new AtomicBoolean();

        /**
         * @return true if you got the lock
         */
        public boolean tryLock() {
            return !busy.getAndSet( true );
        }

        public void unlock() {
            busy.set( false );
        }
    }

    private Queue<LockState> mLockPool = new ConcurrentLinkedQueue<>();
    private Map<String, LockState> mKeyLocks = new HashMap<>();

    /**
     * If you lock, you're responsible for {@link #unlock(String)}
     *
     * @return true if you got the lock
     */
    public synchronized boolean tryLock( String key ) {
        boolean avail = attain( key ).tryLock();
        return avail;
    }

    public synchronized void unlock( String key ) {
        attain( key ).unlock();
        release( key );
    }

    /**
     * Thread that attained should be the one to release, else an error will be thrown upon release.
     */
    private LockState attain( String key ) {
        LockState out = mKeyLocks.get( key );
        if ( out == null ) {
            // lock is not associated with any keys, lets check the pool
            out = mLockPool.poll();
            if ( out == null ) {
                // pool is exhausted, lets expand
                out = new LockState();
                // We'll associate to key, and put back in the pool when released.
                mKeyLocks.put( key, out );
            }
        }
        return out;
    }

    /**
     * Only release if you're the owner
     */
    private void release( String key ) {
        LockState out = mKeyLocks.get( key );
        if ( out == null ) {
            // nothing to release
        } else {
            // lock was in use, lets release it back into the pool
            mKeyLocks.remove( key );
            mLockPool.add( out );
        }
    }

}
