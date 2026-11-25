package com.marketplace.model;

public class Customer extends User {

    private String address;

    public Customer(int id, String name, String email, String phone, String address) {
        super(id, name, email, phone);
        this.address = address;
    }

    @Override
    public String getRole() {
        return "CUSTOMER";
    }

    @Override
    public String display() {
        return super.display() + " | Address: " + address;
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
