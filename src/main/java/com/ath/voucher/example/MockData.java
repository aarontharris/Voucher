package com.ath.voucher.example;

public class MockData {
    private int mId;
    private String mName;
    private String mPhone;

    public MockData( int id ) {
        mId = id;
    }

    public int getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public void setName( String name ) {
        mName = name;
    }

    public String getPhone() {
        return mPhone;
    }

    public void setPhone( String phone ) {
        mPhone = phone;
    }
}
