package com.ath.voucher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Immutable
 */
public class VoucherPayload<DATA> {
    private Exception mError;
    private DATA mData;

    private VoucherPayload() {
    }

    public VoucherPayload( Exception error ) {
        mError = error;
    }

    public VoucherPayload( DATA data ) {
        mData = data;
    }

    static <DATA> List<VoucherPayload<DATA>> filterErrors( Collection<VoucherPayload<DATA>> payloads ) {
        List<VoucherPayload<DATA>> in = new ArrayList<>( payloads ); // copy bcuz we don't know the source and we don't want concurrent mod fails
        List<VoucherPayload<DATA>> out = new ArrayList<>();
        for ( VoucherPayload<DATA> payload : in ) {
            if ( payload.getError() == null ) {
                out.add( payload );
            }
        }
        return out;
    }

    static <DATA> List<VoucherPayload<DATA>> resize( Collection<VoucherPayload<DATA>> payloads, int count ) {
        List<VoucherPayload<DATA>> in = new ArrayList<>( payloads ); // copy bcuz we don't know the source and we don't want concurrent mod fails
        List<VoucherPayload<DATA>> out = in;

        if ( count < in.size() ) {
            int end = in.size();
            int start = in.size() - count;
            out = in.subList( start, end );
        }
        return out;
    }

    public DATA getData() {
        return mData;
    }

    public Exception getError() {
        return mError;
    }

    /**
     * <pre>
     * Voucher guarantees that any unhandled exceptions from end to end of voucher's execution are delivered via the payload.
     * This allows you to confidently deal with success or failure in the callback.
     *
     * If these are Voucher related errors, they will be some derivitive of {@link VoucherException} for identification.
     * All other errors are generated during work execution would be yours and are rethrown as is.
     * </pre>
     *
     * @throws Exception any exceptions that developed during the vouchers execution.
     */
    public void rethrowErrors() throws Exception {
        if ( getError() != null ) {
            throw getError();
        }
    }

    public String describe() {
        if ( getError() != null ) {
            return getError().getMessage();
        }
        return String.valueOf( getData() );
    }

    @Override public String toString() {
        return describe();
    }
}