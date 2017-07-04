package com.ath.voucher;

public class VoucherException extends Exception {

    public VoucherException( String message ) {
        super( message );
    }

    public VoucherException( Exception cause ) {
        super( cause );
    }

    public VoucherException( String message, Exception cause ) {
        super( message, cause );
    }

}
