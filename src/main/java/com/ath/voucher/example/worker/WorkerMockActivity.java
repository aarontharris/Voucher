package com.ath.voucher.example.worker;

import android.support.annotation.NonNull;

import com.ath.voucher.Voucher;
import com.ath.voucher.Voucher.VoucherResponse;
import com.ath.voucher.VoucherException;
import com.ath.voucher.VoucherPayload;
import com.ath.voucher.example.MockData;

public class WorkerMockActivity {
    private SomeWorkerDao mDao = new SomeWorkerDao();

    protected void onResume() {

        // If the event is called after the subscription, the system calls the subscriber and auto-unsubscribes.

        // What if we subscribe after the event is already fired?  I mean... We shouldn't have to care about that right?

        // If the event has already been fired, Voucher will cache the payload for future subscribers.
        // When a cached payload is detected, subscribers are called immediately so no one is left behind.

        // but cache?! The cache is cleared when the VoucherManager goes away or you can clear it yourself

        // READ ME: Voucher is designed to use Lambdas but is built on non-lambda for compat so please excuse the longness in examples

        // Here we ask the dao to request data and let us know when its done
        // if an identical request is made somewhere else in the app we are guaranteed that only 1 will execute.
        // but all subscribers will get notified just the same.

        int dataId = 0;

        mDao.requestData( dataId )
                .setHandlerMain() // optionally tell which thread to reply on
                .setTimeout( 5000 ) // optionally set a timeout guaranteeing a callback
                .subscribe( new VoucherResponse<MockData>() {
                    @Override public void onResult( @NonNull Voucher<MockData> voucher, @NonNull VoucherPayload<MockData> payload ) {
                        try {
                            payload.rethrowErrors(); // whatever error was picked up along the road

                            // Safe as any errors would have been thrown above
                            // Voucher does not guarantee not null, thats up to you and your data
                            MockData data = payload.getData();

                            // TODO play with data

                        } catch ( InterruptedException e ) { // or something...
                            // etc
                        } catch ( VoucherException e ) { // all voucher related errors will extend VoucherException
                            // etc
                        } catch ( Exception e ) {
                            // etc
                        }
                    }
                } );

    }

}
