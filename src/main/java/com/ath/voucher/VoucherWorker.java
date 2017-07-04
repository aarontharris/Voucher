package com.ath.voucher;

import android.support.annotation.Nullable;

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

    @SuppressWarnings( "unchecked" )
    private VoucherManager<Object> vms = VoucherManager.attain();
    private final NonReentrantLockPool mLocks = new NonReentrantLockPool();
    private Executor mExecutor = null;

    public VoucherWorker() {
        mExecutor = initExecutor();
    }

    protected Executor initExecutor() {
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_LIFE_TIME_IN_SECOND,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque( INITAL_CAPACITY )
        );
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
    public final <INPUT, RESULT> Voucher<RESULT> enqueueVoucher( @Nullable String key, final INPUT input, final WorkerTask<INPUT, RESULT> task ) {
        @SuppressWarnings( "unchecked" )
        Voucher<RESULT> voucher = (Voucher<RESULT>) vms.newVoucher( key );
        final String voucherKey = voucher.getKey();

        if ( mLocks.tryLock( voucherKey ) ) {
            try {
                getExecutor().execute( new Runnable() {
                    @Override public void run() {
                        RESULT result = null;
                        Exception error = null;
                        try {
                            result = task.doInBackground( input );
                            // cache ?
                        } catch ( Exception e ) {
                            error = e;
                        } finally {
                            mLocks.unlock( voucherKey );
                        }

                        if ( error != null ) {
                            vms.notifyVouchersClearCache( voucherKey, new VoucherPayload<>( error ) );
                        } else {
                            vms.notifyVouchersClearCache( voucherKey, new VoucherPayload<>( (Object) result ) );
                        }
                    }
                } );
            } catch ( Exception e ) {
                // Failed to attain executor -- should be extremely rare if ever but we want to be thorough
                vms.notifyVouchersClearCache( voucherKey, new VoucherPayload<>( e ) );
                mLocks.unlock( voucherKey );
            }
        }

        return voucher;
    }
}
