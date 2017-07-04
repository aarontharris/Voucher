package com.ath.voucher;

import android.support.annotation.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class WeakAccessor<T> extends WeakReference<T> {

    public WeakAccessor( T referent ) {
        super( referent );
    }

    public WeakAccessor( T referent, ReferenceQueue<? super T> q ) {
        super( referent, q );
    }

    @NeverThrows
    public static <T, OUT> OUT get( WeakAccessor<T> ref, GetWhenNotNull<T, OUT> notNull, @Nullable GetWhenIsNull<OUT> isNull ) {
        try {
            if ( ref != null ) {
                return ref.get( notNull, isNull );
            } else if ( isNull != null ) {
                return isNull.isNull();
            }
        } catch ( Exception e ) {
            Log.e( e );
        }
        return null;
    }

    @NeverThrows
    public static <T> void exe( WeakAccessor<T> ref, DoWhenNotNull<T> notNull, @Nullable DoWhenIsNull isNull ) {
        try {
            if ( ref != null ) {
                ref.exe( notNull, isNull );
            } else if ( isNull != null ) {
                isNull.isNull();
            }
        } catch ( Exception e ) {
            Log.e( e );
        }
    }

    /**
     * Execute the lambda if not Null -- ideal case.<br>
     * <br>
     * If the {@link #get()} value is null, null is returned.<br>
     *
     * @param notNull executed when the {@link WeakReference}'s value is not null
     * @return whatever you return from your lambda
     */
    public <OUT> OUT get( GetWhenNotNull<T, OUT> notNull ) {
        return get( notNull, null );
    }

    /**
     * Execute the left lambda if {@link #get()} is not Null -- ideal case.<br>
     * Execute the right lambda if {@link #get()} is Null -- unideal case. (Optional)<br>
     * <br>
     * If you do not provide a right lambda and the {@link #get()} value is null, null is returned.<br>
     *
     * @param notNull executed when the {@link #get()} value is not null
     * @param isNull  (Optional) executed when the {@link #get()} value is null
     * @return whatever you return from your lambdas
     */
    @NeverThrows
    public <OUT> OUT get( GetWhenNotNull<T, OUT> notNull, @Nullable GetWhenIsNull<OUT> isNull ) {
        OUT out = null;
        try {
            T value = get();
            if ( value == null ) {
                if ( isNull != null ) {
                    out = isNull.isNull();
                }
            } else {
                out = notNull.notNull( value );
            }
        } catch ( Exception e ) {
            Log.e( e );
        }
        return out;
    }

    /**
     * Execute the lambda if {@link #get()} is not Null -- ideal case.<br>
     *
     * @param notNull executed when the {@link #get()} value is not null
     */
    public void exe( DoWhenNotNull<T> notNull ) {
        exe( notNull, null );
    }

    /**
     * Execute the left lambda if {@link #get()} is not Null -- ideal case.<br>
     * Execute the right lambda if {@link #get()} is Null -- unideal case. (Optional)<br>
     *
     * @param notNull executed when the {@link #get()} value is not null
     * @param isNull  (Optional) executed when the {@link #get()} value is null
     */
    @NeverThrows
    public void exe( DoWhenNotNull<T> notNull, @Nullable DoWhenIsNull isNull ) {
        try {
            T value = get();
            if ( value == null ) {
                if ( isNull != null ) {
                    isNull.isNull();
                }
            } else {
                notNull.notNull( value );
            }
        } catch ( Exception e ) {
            Log.e( e );
        }
    }

    public interface GetWhenNotNull<T, OUT> {
        OUT notNull( T value ) throws Exception;
    }

    public interface GetWhenIsNull<OUT> {
        OUT isNull() throws Exception;
    }

    public interface DoWhenNotNull<T> {
        void notNull( T value ) throws Exception;
    }

    public interface DoWhenIsNull {
        void isNull() throws Exception;
    }
}
