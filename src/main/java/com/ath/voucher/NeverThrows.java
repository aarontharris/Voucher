package com.ath.voucher;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * NeverThrows NEVER throws an exception that can be resolved, in otherwords, if an exception is thrown you should just let the app crash.
 * That doesn't mean this is used to denote critical crashes, but rather, "You NEVER need to worry about crashes from this method"
 */
@Documented
@Retention( CLASS )
@Target( { METHOD } )
public @interface NeverThrows {
}
