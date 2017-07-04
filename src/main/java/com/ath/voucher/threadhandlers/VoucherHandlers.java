package com.ath.voucher.threadhandlers;

import static android.os.Looper.getMainLooper;

public class VoucherHandlers {

    private static final VoucherHandler MAIN_HANDLER = new VoucherHandler( getMainLooper() );
    private static final VoucherHandler WORKER_NEW_HANDLER = new WorkerVoucherHandler( true );
    private static final VoucherHandler WORKER_RECYCLED_HANDLER = new RecycledWorkerVoucherHandler( true );

    public static VoucherHandler getMainHandler() {
        return MAIN_HANDLER;
    }

    public static VoucherHandler getWorkerNewHandler() {
        return WORKER_NEW_HANDLER;
    }

    public static VoucherHandler getWorkerRecycledHandler() {
        return WORKER_RECYCLED_HANDLER;
    }

}
