package com.ath.voucher;

import android.support.annotation.AnyThread;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class VoucherWorker {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max( 2, Math.min( CPU_COUNT - 1, 4 ) );
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_LIFE_TIME_IN_SECOND = 30L;
    private static final int INITAL_CAPACITY = 10;

    public interface WorkerTask<INPUT, RESULT> {
        RESULT doInBackground( INPUT input ) throws Exception;
    }

    private static Executor DEFAULT_SHARED_EXECUTOR;

    private static final Executor getDefaultSharedExecutor() {
        if ( DEFAULT_SHARED_EXECUTOR == null ) {
            DEFAULT_SHARED_EXECUTOR = new ThreadPoolExecutor(
                    CORE_POOL_SIZE,
                    MAX_POOL_SIZE,
                    KEEP_LIFE_TIME_IN_SECOND,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque( INITAL_CAPACITY )
            );
        }
        return DEFAULT_SHARED_EXECUTOR;
    }

    @SuppressWarnings( "unchecked" )
    private VoucherManager<Object> vms = VoucherManager.attain();
    private final NonReentrantLockPool mLocks = new NonReentrantLockPool();
    private Executor mExecutor = null;

    public VoucherWorker() {
        mExecutor = initExecutor();
    }

    protected Executor initExecutor() {
        return getDefaultSharedExecutor();
    }

    protected final Executor getExecutor() {
        return mExecutor;
    }

    /**
     * <pre>
     * Basic execution of a {@link Runnable}
     * Uncaught exceptions within runnable will crash the app.
     * </pre>
     */
    public final void enqueue( Runnable run ) {
        getExecutor().execute( run );
    }

    /**
     * Get a voucher in exchange for your request.<br>
     * Allows you to group requests by key so that only the first concurrent request goes async.
     * Subsequent concurrent requests with the same key will be notified when the first completes.
     *
     * @param key  a unique key will be generated if none is provided.
     * @param task
     * @return
     */
    @NeverThrows
    public synchronized final <INPUT, RESULT> Voucher<RESULT> enqueueVoucher( @Nullable String key, final INPUT input, final WorkerTask<INPUT, RESULT> task ) {
        @SuppressWarnings( "unchecked" )
        Voucher<RESULT> voucher = (Voucher<RESULT>) vms.newVoucher( key );
        final String originalKey = key;
        final String generatedKey = voucher.getKey();

        VoucherPayload cachedPayload = cacheGet( originalKey, input ); // the literal key, not the generated one
        if ( cachedPayload != null ) {
            voucher.setCachedPayload( cachedPayload );
            return voucher;
        }

        if ( mLocks.tryLock( generatedKey ) ) {
            try {
                getExecutor().execute( new Runnable() {
                    @Override public void run() {
                        VoucherPayload resultPayload = null;
                        try {
                            resultPayload = cacheGet( originalKey, input ); // the literal key, not the generated one
                            if ( resultPayload == null ) {
                                RESULT result = task.doInBackground( input );
                                resultPayload = new VoucherPayload<>( result );
                                cachePut( originalKey, input, resultPayload ); // the literal key, not the generated one
                            }
                        } catch ( Exception e ) {
                            resultPayload = new VoucherPayload<>( e );
                        } finally {
                            mLocks.unlock( generatedKey );
                        }
                        vms.notifyVouchersClearCache( generatedKey, resultPayload );
                    }
                } );
            } catch ( Exception e ) {
                // Failed to attain executor -- should be extremely rare if ever but we want to be thorough
                vms.notifyVouchersClearCache( generatedKey, new VoucherPayload<>( e ) );
                mLocks.unlock( generatedKey );
            }
        }

        return voucher;
    }

    /**
     * Thread Safe within the scope of the given key<br>
     * If the Payload returned is not null, then the payload will be delivered to the vouchers in stead of executing the work.<br>
     * The payload may have a null value if that is what you wish to return but a null vs not-null payload instance is what determines
     * not-cached or cached respectively.
     * <p>
     * As is, implementing this will prevent a worker thread from being started if a cache-hit is found.
     * <p>
     * PS - you are expected to make sure the input and output type matching is satisfied.  Both input and output should match the input
     * and output assicated with the key used when the request was queued.
     * <p>
     * Should be fast enough to respond on the main thread as this gets called both before starting a worker thread and again within the
     * worker thread before making the network call.<br>
     * Best practice is to the slow work in cachePut
     *
     * @param key
     * @param input
     * @return null if cache is not present, empty-voucher if value is not present.
     */
    @AnyThread
    protected VoucherPayload<?> cacheGet( String key, Object input ) {
        return null;
    }

    /**
     * Thread Safe within the scope of the given key<br>
     * Your hook to plugging the result into the cache.
     * Up to you if you want to cache an error or not.
     * <p>
     * PS - you are expected to make sure the input and output type matching is satisfied.  Both input and output should match the input
     * and output assicated with the key used when the request was queued.
     *
     * @param key
     * @param input
     * @param result
     */
    @WorkerThread
    protected void cachePut( String key, Object input, VoucherPayload<?> result ) {
    }

}
