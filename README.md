# Voucher
Simple and Elegant Callback Mechanism and other Goodies

Voucher is a promise to deliver.

# Philosphy
The root of Voucher's philosophy is:
- It guarantees a callback whether you missed the event or not
- It guarantees any errors discovered along it's path will be delivered back to the listener
- It won't leak
- No bother to unsubscribe

This makes for very simple code.  No need to worry about lost listeners, leaks, unsubscribing.
No need to catch errors outside the callback and within.  No need to worry about whether or not your callback will actually get called forcing you to take annoying precautions.
No need to worry about whether the event has already been fired -- if so, you'll get notified immediately.
Want to be called back on a specific thread, we got you covered.

Pseudo Example:
```
Voucher voucher = newVoucher()
voucher.subscribe( ( payload ) -> {
  // guaranteed to receive payload.data or payload.error
})

// Elsewhere...
voucherManager.notify( data or error )
```


