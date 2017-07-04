package com.ath.voucher.example.simple;

import com.ath.voucher.Voucher;
import com.ath.voucher.VoucherManager;
import com.ath.voucher.VoucherPayload;
import com.ath.voucher.example.MockData;

/**
 * SimpleEventManager deals specifically with the "someEvent"<br>
 * This allows outsiders to easily subscribe and fire without concerning themselves with the details
 */
public class SimpleEventManager {
    private static final String VOUCHER_KEY = "someEvent";
    private VoucherManager<MockData> mVoucherManager = VoucherManager.attain();

    public Voucher<MockData> newVoucher() {
        return mVoucherManager.newVoucher( VOUCHER_KEY );
    }

    public void notifySomeEvent( MockData data, Exception err ) {
        if ( err != null ) {
            mVoucherManager.notifyVouchers( VOUCHER_KEY, new VoucherPayload<MockData>( err ) );
        } else {
            mVoucherManager.notifyVouchers( VOUCHER_KEY, new VoucherPayload<MockData>( data ) );
        }
    }
}
