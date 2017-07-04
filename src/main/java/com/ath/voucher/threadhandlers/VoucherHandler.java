package com.ath.voucher.threadhandlers;

import android.os.Handler;
import android.os.Looper;

import com.ath.voucher.Voucher;
import com.ath.voucher.Voucher.VoucherResponse;
import com.ath.voucher.VoucherPayload;

public class VoucherHandler {
    private Handler mHandler;
    private boolean mWorker;

    VoucherHandler( boolean worker ) {
        this.mWorker = worker;
    }

    VoucherHandler( Looper looper ) {
        mHandler = new Handler( looper );
    }

    public <DATA> void sendMessage( final Voucher<DATA> voucher, final VoucherPayload<DATA> payload, final VoucherResponse<DATA> response ) {
        mHandler.post( new Runnable() {
            @Override public void run() {
                try {
                    response.onResult( voucher, payload );
                } catch ( Exception e ) {
                    e.printStackTrace(); // FIXME: provide a way to deliver this error - general error handler?
                }
            }
        } );
    }

    boolean isWorker() {
        return mWorker;
    }
}