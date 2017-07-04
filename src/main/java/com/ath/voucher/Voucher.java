package com.ath.voucher;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.ath.voucher.WeakAccessor.DoWhenIsNull;
import com.ath.voucher.WeakAccessor.DoWhenNotNull;
import com.ath.voucher.WeakAccessor.GetWhenIsNull;
import com.ath.voucher.WeakAccessor.GetWhenNotNull;
import com.ath.voucher.threadhandlers.VoucherHandler;
import com.ath.voucher.threadhandlers.VoucherHandlers;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;


/**
 * This class serves as your "Voucher", it will get fulfilled at a later time.<br>
 * Subscribing to this voucher will get you one callback upon success or fail.<br>
 * If you do not {@link #resubscribe()} you won't get called again.<br>
 * <br>
 * Think Future or Observable -- but simplified for the sake of easy to use.<br>
 */
public class Voucher<DATA> {
    private static final VoucherHandler MAIN_HANDLER = VoucherHandlers.getMainHandler();
    private static final VoucherHandler WORKER_NEW_HANDLER = VoucherHandlers.getWorkerNewHandler();
    private static final VoucherHandler WORKER_RECYCLED_HANDLER = VoucherHandlers.getWorkerRecycledHandler();
    private final String mId;
    private final String mName;
    private WeakAccessor<VoucherManager<DATA>> mManager;
    private VoucherResponse<DATA> mListener;
    private Watcher mWatcher;
    private Object mWatcherLock = new Object();
    private VoucherHandler mHandler;
    private VoucherPayload<DATA> mErrPayload;
    private Long mTimeoutMillis;
    private boolean mEnabled = true;
    private LinkedList<VoucherPayload<DATA>> mDisabledPayloads;

    Voucher( VoucherManager<DATA> manager, String idKey ) {
        mManager = new WeakAccessor<>( manager );
        mName = idKey;
        if ( idKey == null || idKey.isEmpty() ) {
            idKey = UUID.randomUUID().toString();
        }
        mId = idKey;
    }

    private void destroy() {
        mManager = null;
        mListener = null;
        mWatcher = null;
        mHandler = null;
        mErrPayload = null;
        mEnabled = false;
    }

    /**
     * Guaranteed non-null identifier for this voucher
     * but not necessarily what the user entered.
     */
    @NonNull public String getKey() {
        return mId;
    }

    /**
     * May be null and is exactly what the user entered.
     */
    @Nullable public String getName() {
        return mName;
    }

    /**
     * Is this Voucher registered with the VoucherManager.
     */
    public boolean isRegistered() {
        return WeakAccessor.get( mManager, new GetWhenNotNull<VoucherManager<DATA>, Boolean>() {
            @Override public Boolean notNull( VoucherManager<DATA> m ) throws Exception {
                return m.isRegistered( Voucher.this );
            }
        }, new GetWhenIsNull<Boolean>() {
            @Override public Boolean isNull() throws Exception {
                return false;
            }
        } );
    }

    /**
     * Does this Voucher have a listener
     */
    public boolean isSubscribed() {
        return mListener != null;
    }

    /**
     * Auto unsubscribes after one call unless you call {@link #resubscribe()}<br>
     * Calling this again will overwrite the previous listener.
     *
     * @param listener
     * @return
     */
    public final Voucher<DATA> subscribe( @NonNull VoucherResponse<DATA> listener ) {
        this.mListener = listener;
        WeakAccessor.exe( mManager, new DoWhenNotNull<VoucherManager<DATA>>() {
            @Override public void notNull( VoucherManager<DATA> m ) throws Exception {
                m.notifyVoucher( Voucher.this );
            }
        }, new DoWhenIsNull() {
            @Override public void isNull() throws Exception {
                Voucher.this.notifySubscriber( new VoucherPayload<DATA>( new Exception( "This Voucher's Manager has been destroyed" ) ) );
            }
        } );
        return this;
    }

    final VoucherPayload<DATA> getPayload() {
        return WeakAccessor.get( mManager, new GetWhenNotNull<VoucherManager<DATA>, VoucherPayload<DATA>>() {
            @Override public VoucherPayload<DATA> notNull( VoucherManager<DATA> m ) throws Exception {
                return m.getCachedPayload( mId );
            }
        }, null );
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    @MainThread
    public void disable() {
        mEnabled = false;
    }

    @MainThread
    public void enable() {
        if ( !isEnabled() ) {
            mEnabled = true;
            if ( mDisabledPayloads != null ) {
                WeakAccessor.exe( mManager, new DoWhenNotNull<VoucherManager<DATA>>() {
                    @Override public void notNull( VoucherManager<DATA> m ) throws Exception {
                        VoucherPayload<DATA> payload = mDisabledPayloads.peekLast();

                        if ( mDisabledPayloads.size() > 1 ) {
                            List<VoucherPayload<DATA>> payloads = VoucherPayload.filterErrors( mDisabledPayloads );
                            mDisabledPayloads.clear();
                            if ( payloads.size() > 0 ) {
                                payload = payloads.get( payloads.size() - 1 );
                            }
                        }

                        mDisabledPayloads.clear();

                        if ( payload != null ) {
                            m.notifyVoucher( Voucher.this, payload );
                        }
                    }
                }, null );
            }
        }
    }

    /**
     * Pull it out of the manager, unless you're holding onto the voucher, its gone.
     */
    public void unregister() {
        cancelTimer();
        WeakAccessor.exe( mManager, new DoWhenNotNull<VoucherManager<DATA>>() {
            @Override public void notNull( VoucherManager<DATA> m ) throws Exception {
                m.unregister( Voucher.this );
            }
        }, null );
    }

    /**
     * The Voucher is automatically unregistered from the VoucherManager after the subscription is fulfilled.<br>
     * If you want subsequent notifications in a repeated notification scenario, you can resubscribe.<br>
     * Note that by doing so, you are responsible for unregistering your Voucher or else it becomes a leak.<br>
     * <br>
     * Consider {@link #linkParent(VoucherAware)}<br>
     */
    public void resubscribe() {
        WeakAccessor.exe( mManager, new DoWhenNotNull<VoucherManager<DATA>>() {
            @Override public void notNull( VoucherManager<DATA> m ) throws Exception {
                m.register( Voucher.this.setTimeout( mTimeoutMillis ) );
            }
        }, null );
    }

    final int getTimeoutDefault() {
        int timeout = WeakAccessor.get( mManager, new GetWhenNotNull<VoucherManager<DATA>, Integer>() {
            @Override public Integer notNull( VoucherManager<DATA> m ) throws Exception {
                return m.getDefaultVoucherTimeoutMillis();
            }
        }, new GetWhenIsNull<Integer>() {
            @Override public Integer isNull() throws Exception {
                return VoucherManager.DEFAULT_TIMEOUT;
            }
        } );
        return timeout;
    }

    public final Voucher<DATA> setTimeoutDefault() {
        return setTimeout( getTimeoutDefault() );
    }

    public final Voucher<DATA> setTimeout( Integer millis ) {
        Long longMillis = new Long( millis );
        return setTimeout( longMillis );
    }

    /**
     * Automatically canceled when when the voucher is notified from another source or unsubscribed
     *
     * @param millis
     * @return
     */
    @ThreadSafe
    public final Voucher<DATA> setTimeout( Long millis ) {
        // FIXME: @aaronharris 3/12/17 what about when the app goes into the background, the timer stops but the thread probly doesnt? test?
        // Instead maybe implement a wrapper around Android Handler to simplify (see below)
        // then once you have that, you can pause and resume when the app goes away and comes back?

        // FIXME: @aaronharris 3/10/17 update voucher to use a handler instead of a watcher
        // will need to get a unique id, maybe atomicLong.incAndGet() managed by the VoucherManager?
        // that unique id is the message.what for the handler.sendMessageDelayed
        // the unique id is also saved in the voucher
        // if the voucher receives its callback, the unique id is used to cancel the message in the handler.
        // if setTimeout is called again, the same uniqueid is used and previous messages are canceled.
        // if someone resubscribes, should resubscribe reset the timeout or should they have to do it themselves in the callback?

        // remember for resubscribe
        mTimeoutMillis = millis;

        // just in case we've called before
        cancelTimer();

        // do it
        if ( mTimeoutMillis != null ) {
            synchronized ( mWatcherLock ) {
                mWatcher = new Watcher( mTimeoutMillis ) {
                    @Override
                    protected void onTimeExceeded() {
                        Voucher.this.notifySubscriber( new VoucherPayload<DATA>( new TimeoutException( "Timeout Exceeded " + mTimeoutMillis + "ms" ) ) );
                    }
                }.start();
            }
        }
        return this;
    }

    @ThreadSafe
    public final Voucher<DATA> cancelTimer() {
        synchronized ( mWatcherLock ) {
            if ( mWatcher != null ) {
                mWatcher.cancel();
                mWatcher = null;
            }
            return this;
        }
    }

    /**
     * Warning this gets cached for the life of the Voucher (Not the manager).<br>
     * This is returned in case of any error, convenient when you just don't care.<br>
     * Errors are still logged.
     *
     * @param data
     * @return
     */
    public final Voucher<DATA> setErrPayload( final DATA data ) {
        mErrPayload = new VoucherPayload<>( data );
        return this;
    }

    /**
     * By specifying a parent, the child-voucher will follow the parent in lifecycle and death.<br>
     * If the parent is lifecycle aware and goes to pause/resume/etc, the subscription will get paused/resumed/etc.<br>
     * If the parent is garbage collected, the child-voucher is garbage collected.<br>
     * <br>
     * Lifecycle Aware:<br>
     * {@link VoucherAware}
     *
     * @param parent
     * @return
     */
    public final Voucher<DATA> linkParent( final VoucherAware parent ) {
        WeakAccessor.exe( mManager, new DoWhenNotNull<VoucherManager<DATA>>() {
            @Override public void notNull( VoucherManager<DATA> m ) throws Exception {
                m.linkParent( Voucher.this, parent );
            }
        }, null );
        return this;
    }

    /**
     * Indicate that you'd like to be called-back on AnyThread - no specific requirements.
     */
    public final Voucher<DATA> setHandlerAny() {
        return setHandler( null );
    }

    /**
     * Indicate that you'd like to be called-back on the MainThread.
     */
    public final Voucher<DATA> setHandlerMain() {
        return setHandler( MAIN_HANDLER );
    }

    /**
     * Indicate that you'd like to be called-back on a shiny new background thread - not the same one the blocking work was executed on.<br>
     */
    public final Voucher<DATA> setHandlerWorker() {
        return setHandler( WORKER_NEW_HANDLER );
    }

    /**
     * Indicate that you'd like to be called-back on the same worker thread that was used to execute the task if any.<br>
     * If the task was on the main thread, a new worker will be issued.<br>
     * <p>
     * Thus - we're recycling the existing worker if it exists instead of creating a new one.<br>
     * Though be considerate of holding up this thread, in case others are also subscribed via RECYCLED worker.<br>
     * In this case, the callbacks will be serialized and others will wait.
     */
    public final Voucher<DATA> setHandlerWorkerRecycled() {
        return setHandler( WORKER_RECYCLED_HANDLER );
    }

    /**
     * The handler bound to the thread you'd like to be called back on.<br>
     * Make sure that this thread is going to exist when this callback is returned!<br>
     *
     * @param handler optional - null will not be picky and call back on whatever thread the result happened to be on, worker or ui.
     */
    public final Voucher<DATA> setHandler( @Nullable VoucherHandler handler ) {
        this.mHandler = handler;
        return this;
    }

    final synchronized void notifySubscriber( VoucherPayload<DATA> payload ) {
        if ( isRegistered() ) { // protect against synchronized pile-up

            // The VoucherManager may process a Voucher that has not yet Voucher.subscribe() - this is by design.
            // Because we want to honor the voucher regardless of use of the voucher (you may not want to Voucher.subscribe() but instead Voucher.getPayload() later)
            // However while this concurrently safe, it is also concurrently confusing because we unsubscribe the voucher from the manager even though the Voucher may get a Voucher.subscribe() called later.
            // We are protected because the Voucher.subscribe() checks for cached payloads and calls through to the subscription whether the Voucher is subscribed in the manager or not.

            unregister();
            if ( mListener != null ) {
                try {
                    VoucherPayload<DATA> myPayload = payload;
                    if ( myPayload.getError() != null && mErrPayload != null ) {
                        myPayload = mErrPayload;
                    }

                    if ( isEnabled() ) {
                        if ( mHandler != null ) {
                            mHandler.sendMessage( this, myPayload, mListener );
                        } else {
                            mListener.onResult( this, myPayload );
                        }
                    } else {
                        resubscribe();
                        if ( mDisabledPayloads == null ) {
                            mDisabledPayloads = new LinkedList<>();
                        }
                        mDisabledPayloads.add( myPayload );
                    }
                } catch ( Exception e ) {
                    Log.e( e );
                }
            }

            // Deal with some hair circumstances
            Exception err = payload.getError();
            if ( err != null ) {

                // honor the last will and testament of our beloved parent
                // who's will just happens to take us down with them... SOBs
                if ( err instanceof VoucherParentDestroyedException ) {
                    // even though we do an unregister above
                    // we do it again just in case the subscriber resubscribed
                    // we dont want a lingering connection
                    unregister();
                    destroy();
                }
            }
        }
    }

    public interface VoucherAware {
        /**
         * Must support multiple callbacks and call through to each lifecycle method at the appropriate time.
         *
         * @param callback
         */
        void addVoucherLifecycleCallbacks( @NonNull VoucherLifecycleCallbacks callback );

        void remVoucherLifecycleCallbacks( @NonNull VoucherLifecycleCallbacks callback );
    }

    public interface VoucherLifecycleCallbacks {
        /**
         * @param object is not held
         */
        void onCreate( VoucherAware object );

        /**
         * @param object is not held
         */
        void onResume( VoucherAware object );

        /**
         * @param object is not held
         */
        void onPause( VoucherAware object );

        /**
         * @param object is not held
         */
        void onDestroy( VoucherAware object );
    }

    public interface VoucherResponse<DATA> {
        /**
         * You must be very very quick here, don't hold up this thread.<br>
         * You are not guaranteed that this will get called on any particular thread.<br>
         *
         * @param payload
         */
        void onResult( @NonNull Voucher<DATA> voucher, @NonNull VoucherPayload<DATA> payload );
    }

}
