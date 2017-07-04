package com.ath.voucher;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.ath.voucher.Voucher.VoucherAware;
import com.ath.voucher.Voucher.VoucherLifecycleCallbacks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * Manages Vouchers and their subscribers by topic.<br>
 * <br>
 * Beware the default {@link VoucherPayload} cache policy, see {@link #setCachedPayloadTimeout(String, long)}.<br>
 */
public class VoucherManager<DATA> {

    /**
     * Generates a namespaced key localized to the owner.<br>
     * If the same owner calls the {@link #obtainLocalizedKey(Object, String)} twice with the same key, they will get the same value.<br>
     * If another owner calls with the same key, the returned value will be localized to that owner.
     *
     * @param owner
     * @param key
     * @return
     */
    public static String obtainLocalizedKey( @NonNull Object owner, @NonNull String key ) {
        return key + "." + owner.getClass().getName() + "[" + Integer.toHexString( owner.hashCode() ) + "]";
    }

    static final int DEFAULT_TIMEOUT = 2000;
    private int mDefaultVoucherTimeoutMillis = DEFAULT_TIMEOUT;
    private ReentrantReadWriteLock mLock = new ReentrantReadWriteLock(); // consider hashing by key?
    private Map<String, ConcurrentLinkedQueue<Voucher<DATA>>> mVouchers = Collections.synchronizedMap( new HashMap<String, ConcurrentLinkedQueue<Voucher<DATA>>>() );
    private Map<String, VoucherPayload<DATA>> mLastPayload = new ConcurrentHashMap<>();
    private WeakHashMap<Object, ConcurrentLinkedQueue<Voucher<DATA>>> mParentChildren = new WeakHashMap<>();
    private final VoucherLifecycleCallbacks mVoucherLifecycleCallbacks = new VoucherLifecycleCallbacks() {
        @Override public void onCreate( VoucherAware object ) {
            //getChildren( object )
        }

        @Override public void onResume( VoucherAware object ) {
            for ( Voucher v : getChildren( object ) ) {
                v.enable();
            }
        }

        @Override public void onPause( VoucherAware object ) {
            for ( Voucher v : getChildren( object ) ) {
                v.disable();
            }
        }

        @Override public void onDestroy( VoucherAware object ) {
            VoucherPayload payload = new VoucherPayload( new VoucherParentDestroyedException( String.format( "%s was destroyed", object ) ) );
            for ( Voucher v : getChildren( object ) ) {
                //noinspection unchecked
                v.notifySubscriber( payload );
            }
        }
    };

    private VoucherManager() {
    }

    private ReadLock readLock( String key ) {
        return mLock.readLock();
    }

    private WriteLock writeLock( String key ) {
        return mLock.writeLock();
    }

    @MainThread
    @NeverThrows
    public static VoucherManager attain() {
        VoucherManager manager = new VoucherManager();
        return manager;
    }

    public static VoucherManager attain( int defaultVoucherTimeoutMillis ) {
        VoucherManager vm = attain();
        if ( defaultVoucherTimeoutMillis < 0 ) {
            vm.mDefaultVoucherTimeoutMillis = DEFAULT_TIMEOUT;
        } else {
            vm.mDefaultVoucherTimeoutMillis = defaultVoucherTimeoutMillis;
        }
        return vm;
    }

    public int getDefaultVoucherTimeoutMillis() {
        return mDefaultVoucherTimeoutMillis;
    }

    @ThreadSafe
    void linkParent( Voucher<DATA> voucher, Object parent ) {
        if ( parent instanceof Voucher.VoucherAware ) {
            ( (VoucherAware) parent ).addVoucherLifecycleCallbacks( mVoucherLifecycleCallbacks );
        }

        getChildren( parent ).add( voucher );
    }

    @ThreadSafe
    void unlinkParent( Voucher<DATA> voucher, Object parent ) {
        getChildren( parent ).remove( voucher );
    }

    @ThreadSafe
    @NonNull
    private ConcurrentLinkedQueue<Voucher<DATA>> getChildren( Object parent ) {
        synchronized ( mParentChildren ) {
            ConcurrentLinkedQueue<Voucher<DATA>> children = mParentChildren.get( parent );
            if ( children == null ) {
                children = new ConcurrentLinkedQueue<>();
                mParentChildren.put( parent, children );
            }
            return children;
        }
    }

    private Collection<Voucher<DATA>> getVouchers( String key ) {
        ConcurrentLinkedQueue<Voucher<DATA>> vouchers = mVouchers.get( key );
        if ( vouchers == null ) {
            vouchers = new ConcurrentLinkedQueue<>();
            mVouchers.put( key, vouchers );
        }
        return vouchers;
    }

    /**
     * Default {@link VoucherPayload} Cache Policy<br>
     * is to cache the {@link VoucherPayload} indefinitely.
     * <p>
     * When the given millis has elapsed, the payload for the given key will be cleared.<br>
     * The timer resets when the payload is set.
     *
     * @param millis
     */
    public void setCachedPayloadTimeout( @NonNull String key, long millis ) {
        // FIXME: 3/12/17 @aarontharris - make setCachedPayloadTimeout work
    }

    /**
     * Clears the local payload cache.  Subsequent vouchers will not be honored until the payload is redelivered!
     */
    public void clearCachedPayload( String key ) {
        if ( key != null ) {
            writeLock( key ).lock();
            try {
                mLastPayload.remove( key );
            } finally {
                writeLock( key ).unlock();
            }
        }
    }

    /**
     * Warning -- this is not guaranteed immutable
     */
    @Nullable
    VoucherPayload<DATA> getCachedPayload( @NonNull String key ) {
        return mLastPayload.get( key );
    }

    public void notifyVouchers( @NonNull String key, @NonNull VoucherPayload<DATA> payload ) {
        notifyVouchers( key, payload, false );
    }

    public void notifyVouchersClearCache( @NonNull String key, @NonNull VoucherPayload<DATA> payload ) {
        notifyVouchers( key, payload, true );
    }

    /**
     * This payload and it's data/error is cached until {@link #clearCachedPayload(String)}, {@link #setCachedPayloadTimeout(String, long)} or the VoucherManager is GCd
     * .<br>
     * Be careful of leaks.
     */
    @NeverThrows
    public void notifyVouchers( @NonNull String key, @NonNull VoucherPayload<DATA> payload, boolean clearCache ) {
        // writeLock: only notify when no oustanding calls to newVoucher().
        writeLock( key ).lock();
        try {
            if ( !clearCache ) {
                mLastPayload.put( key, payload );
            }
            Collection<Voucher<DATA>> vouchers = new ArrayList<>( getVouchers( key ) );
            if ( vouchers.size() > 0 ) {
                for ( Voucher<DATA> voucher : vouchers ) {
                    try {
                        if ( voucher != null ) {
                            voucher.notifySubscriber( payload );
                        }
                    } catch ( Exception e ) {
                        Log.e( e );
                    }
                }
            }
        } finally {
            writeLock( key ).unlock();
        }
    }

    public int voucherCount( @NonNull String key ) {
        readLock( key ).lock();
        try {
            return getVouchers( key ).size();
        } finally {
            readLock( key ).unlock();
        }
    }

    /**
     * Notify the given voucher with the VoucherManager's cached payload for the Vouchers key
     *
     * @param voucher
     */
    void notifyVoucher( Voucher<DATA> voucher ) {
        // readLock: allow multiple newVoucher() calls to be requested concurrenly but not during notifyVouchers()
        readLock( voucher.getKey() ).lock();
        try {
            VoucherPayload<DATA> copyPayload = mLastPayload.get( voucher.getKey() ); // protect against external mutations
            if ( copyPayload != null ) {
                voucher.notifySubscriber( copyPayload );
            }
        } finally {
            readLock( voucher.getKey() ).unlock();
        }
    }

    /**
     * Notify the given voucher with the given payload
     *
     * @param voucher
     * @param payload
     */
    void notifyVoucher( Voucher<DATA> voucher, VoucherPayload<DATA> payload ) {
        // readLock: allow multiple newVoucher() calls to be requested concurrenly but not during notifyVouchers()
        readLock( voucher.getKey() ).lock();
        try {
            if ( payload == null ) {
                payload = new VoucherPayload<>( (DATA) null );
            }
            voucher.notifySubscriber( payload );
        } finally {
            readLock( voucher.getKey() ).unlock();
        }
    }

    /**
     * Notify the given voucher with the given payloads
     *
     * @param voucher
     * @param payloads
     */
    void notifyVoucher( Voucher<DATA> voucher, Collection<VoucherPayload<DATA>> payloads ) {
        // readLock: allow multiple newVoucher() calls to be requested concurrenly but not during notifyVouchers()
        readLock( voucher.getKey() ).lock();
        try {
            for ( VoucherPayload<DATA> payload : payloads ) {
                voucher.notifySubscriber( payload );
            }
        } finally {
            readLock( voucher.getKey() ).unlock();
        }
    }

    /**
     * Generates a voucher with a random key
     */
    public Voucher<DATA> newVoucher() {
        return newVoucher( null );
    }

    @NeverThrows
    public Voucher<DATA> newVoucher( String key ) {
        Voucher<DATA> voucher = new Voucher<>( this, key );
        register( voucher );
        return voucher;
    }

    /**
     * This will remove any reference to the voucher from the VoucherManager and unsubscribe it.
     */
    void unregister( @NonNull Voucher<DATA> voucher ) {
        if ( voucher != null ) {
            ConcurrentLinkedQueue<Voucher<DATA>> vouchers = mVouchers.get( voucher.getKey() );
            if ( vouchers != null ) {
                vouchers.remove( voucher );
            }
        }
    }

    boolean isRegistered( @NonNull Voucher<DATA> voucher ) {
        return getVouchers( voucher.getKey() ).contains( voucher );
    }

    /**
     * This will add the voucher to the VoucherManager allowing it to receive messages.<br>
     * If one were to hold onto this Voucher, they could resubscribe to it later as long as the VoucherManager has not fallen out of scope.<br>
     * <p>
     * SEE {@link VoucherManager}
     */
    void register( @NonNull Voucher<DATA> voucher ) {
        getVouchers( voucher.getKey() ).add( voucher );
    }

}
