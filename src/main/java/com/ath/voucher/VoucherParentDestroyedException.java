package com.ath.voucher;

import com.ath.voucher.Voucher.VoucherAware;
import com.ath.voucher.Voucher.VoucherLifecycleCallbacks;

/**
 * If you're receiving this, you should make peace and die.<br>
 * Resubscribing won't help you.<br>
 * <p>
 * This is thrown when a {@link Voucher} is linked to a parent {@link VoucherAware}
 * and the parent is {@link VoucherLifecycleCallbacks#onDestroy(VoucherAware)}
 */
public class VoucherParentDestroyedException extends VoucherException {

    VoucherParentDestroyedException( String message ) {
        super( message );
    }

}
