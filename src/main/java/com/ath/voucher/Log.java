package com.ath.voucher;

class Log {
    private static String LOG_TAG = "LOGGER";
    private static boolean DEBUG = true;

    public static final void init( String logTag, boolean debug ) { // simple control for now
        LOG_TAG = logTag;
        DEBUG = debug;
    }

    public static final void v( String format, Object... args ) {
        if ( DEBUG ) {
            android.util.Log.v( LOG_TAG, String.format( format, args ) );
        }
    }

    public static final void d( String format, Object... args ) {
        if ( DEBUG ) {
            android.util.Log.d( LOG_TAG, String.format( format, args ) );
        }
    }

    public static final void i( String format, Object... args ) {
        if ( DEBUG ) {
            android.util.Log.i( LOG_TAG, String.format( format, args ) );
        }
    }

    public static final void w( String format, Object... args ) {
        if ( DEBUG ) {
            android.util.Log.w( LOG_TAG, String.format( format, args ) );
        }
    }

    public static final void e( Throwable t, String format, Object... args ) {
        android.util.Log.e( LOG_TAG, String.format( format, args ), t );
    }

    public static final void e( Throwable t ) {
        android.util.Log.e( LOG_TAG, t.getMessage(), t );
    }

    public static void t( String format, Object... args ) {
        if ( DEBUG ) { // fixme test
            android.util.Log.v( LOG_TAG, String.format( format, args ) );
        }
    }
}
