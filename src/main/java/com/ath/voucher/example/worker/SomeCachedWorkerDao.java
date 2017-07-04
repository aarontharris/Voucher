package com.ath.voucher.example.worker;

import com.ath.voucher.Voucher;
import com.ath.voucher.VoucherManager;
import com.ath.voucher.VoucherPayload;
import com.ath.voucher.VoucherWorker;
import com.ath.voucher.VoucherWorker.WorkerTask;
import com.ath.voucher.example.MockData;

import java.util.HashMap;
import java.util.Map;

/**
 * SimpleEventManager deals specifically with the "someEvent"<br>
 * This allows outsiders to easily subscribe and fire without concerning themselves with the details
 */
public final class SomeCachedWorkerDao {
    private static final SomeCachedWorkerDao SELF = new SomeCachedWorkerDao();

    // Why a singleton?  Because anyone in the app that wants this data should talk to the same
    // dao so that dao can identify and batch identical requests.
    // best to use dependency injection but thats outside the scope of this example
    // For Dependency Injection SEE: https://github.com/aarontharris/Fuel
    private static final SomeCachedWorkerDao get() {
        return SELF;
    }

    // This key is guaranteed localized to this Dao
    // Any events subscribed for fired with this key will be grouped together
    // such that only the first will do the actual work, all others will skip the work and depend on the first
    // and receive their callback.
    private final String VOUCHER_KEY = VoucherManager.obtainLocalizedKey( this, "SOME_EVENT" );

    // To support caching the easy way, we just override the cache hooks the worker provides
    private VoucherWorker mWorker = new VoucherWorker() {

        // In this case we're simply caching a single object so no need for anything fancy
        @Override protected void cachePut( String key, Object input, VoucherPayload<?> result ) {
            // we dont care about when there's an error or no data
            // for our example case we only want to cache non-null
            if ( result.getData() != null ) {
                mMockDataCache.put( (Integer) input, (MockData) result.getData() );
            }
        }

        // If its in our silly cache then we return it
        // no concerns about concurrency, they're taken care of for us
        @Override protected VoucherPayload cacheGet( String key, Object input ) {
            if ( mMockDataCache.containsKey( (Integer) input ) ) {
                return new VoucherPayload( mMockDataCache.get( (Integer) input ) );
            }
            return null; // not in the cache
        }
    };

    // The worst cache ever, but good enough for our example :/
    private Map<Integer, MockData> mMockDataCache = new HashMap<>();

    // Notice this Dao is using a singleton worker.
    // This is how we gain the benefit of grouping identical requests
    public synchronized Voucher<MockData> requestData( final int mockDataId ) {

        // Notice we had to use synchronized (or locks if this were a real world example)
        // We lock here because we're going to check the cache and potentially make a request
        // those two operations combined are not atomic so we need to lock

        // Automatically notifies any pending vouchers when the task is complete
        // Once the vouchers are notified the voucher-cache is cleared
        // So only duplicate requests made during this execution window are batched
        // once finished, a new request with this key will be executed
        // Its up to you to build a long-term cache to decide if you want to really do that work or not.
        // this example does not utilize a cache.
        return mWorker.enqueueVoucher( VOUCHER_KEY + mockDataId, null, new WorkerTask<Void, MockData>() {
            @Override public MockData doInBackground( Void aVoid ) throws Exception {
                Thread.sleep( 500 ); // thrown errors are auto-plugged into the voucher response payload
                MockData data = new MockData( mockDataId );
                data.setName( "Joe" );
                data.setPhone( "8675309" );
                return data;
            }
        } );
    }
}
