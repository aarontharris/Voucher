package com.ath.voucher.threadhandlers;

import android.os.Looper;

import com.ath.voucher.Voucher;
import com.ath.voucher.VoucherPayload;

class WorkerVoucherHandler extends VoucherHandler {
    WorkerVoucherHandler( boolean worker ) {
        super( worker );
    }

    WorkerVoucherHandler( Looper looper ) {
        super( looper );
    }

    @Override
    public <DATA> void sendMessage( final Voucher<DATA> voucher, final VoucherPayload<DATA> payload, final Voucher.VoucherResponse<DATA> response ) {
        Thread t = new Thread( new Runnable() {
            @Override public void run() {
                try {
                    response.onResult( voucher, payload );
                } catch ( Exception e ) {
                    e.printStackTrace(); // FIXME: provide a way to deliver this error - general error handler?
                }
            }
        } );
        t.start();
    }
}
