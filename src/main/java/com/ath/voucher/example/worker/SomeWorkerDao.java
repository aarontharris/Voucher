package com.ath.voucher.example.worker;

import com.ath.voucher.Voucher;
import com.ath.voucher.VoucherManager;
import com.ath.voucher.VoucherWorker;
import com.ath.voucher.VoucherWorker.WorkerTask;
import com.ath.voucher.example.MockData;

/**
 * SimpleEventManager deals specifically with the "someEvent"<br>
 * This allows outsiders to easily subscribe and fire without concerning themselves with the details
 */
public class SomeWorkerDao {

    // This key is guaranteed localized to this Dao
    // Any events subscribed for fired with this key will be grouped together
    // such that only the first will do the actual work, all others will skip the work and depend on the first
    // and receive their callback.
    private final String VOUCHER_KEY = VoucherManager.obtainLocalizedKey( this, "SOME_EVENT" );
    private VoucherWorker mWorker = new VoucherWorker();

    // Notice this Dao is using a singleton worker.
    // This is how we gain the benefit of grouping identical requests
    public Voucher<MockData> requestData( final int mockDataId ) {

        // no need to synchronize at this point since the worker.enqueue is thread safe
        // as long as its one atomic operation like you see below

        // Automatically notifies any pending vouchers when the task is complete
        // Once the vouchers are notified the voucher-cache is cleared
        // So only duplicate requests made during this execution window are batched
        // once finished, a new request with this key will be executed
        // Its up to you to build a long-term cache to decide if you want to really do that work or not.
        // this example does not utilize a cache.
        return mWorker.enqueueVoucher( VOUCHER_KEY + mockDataId, null, new WorkerTask<Void, MockData>() {
            // notice the key we pass into enqueueVoucher is unique to the event and request (includes id)
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
