package com.ath.voucher.threadhandlers;

import android.os.Looper;

import com.ath.voucher.Voucher;
import com.ath.voucher.Voucher.VoucherResponse;
import com.ath.voucher.VoucherPayload;

class RecycledWorkerVoucherHandler extends WorkerVoucherHandler {
    RecycledWorkerVoucherHandler( boolean worker ) {
        super( worker );
    }

    RecycledWorkerVoucherHandler( Looper looper ) {
        super( looper );
    }

    @Override
    public <DATA> void sendMessage( final Voucher<DATA> voucher, final VoucherPayload<DATA> payload, final VoucherResponse<DATA> response ) {
        boolean isMainThread = Looper.getMainLooper().getThread().getId() == Thread.currentThread().getId();
        if ( isMainThread ) {
            super.sendMessage( voucher, payload, response );
        } else {
            try {
                response.onResult( voucher, payload );
            } catch ( Exception e ) {
                e.printStackTrace(); // FIXME: provide a way to deliver this error - general error handler?
            }
        }
    }
}