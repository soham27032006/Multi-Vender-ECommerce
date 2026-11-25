package com.marketplace.model;

import java.util.Objects;

/** Base class: common fields live here, role-specific behaviour in subclasses. */
public abstract class User {

    private int id;
    private String name;
    private String email;
    private String phone;

    protected User(int id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    /** Each subclass must say what it is (runtime polymorphism). */
    public abstract String getRole();

    /** Subclasses override to add their own fields; base prints shared part. */
    public String display() {
        return String.format("[%s] #%d %s | %s | %s", getRole(), id, name, email, phone);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    // Identity is the DB id, so two objects for the same row are equal in Sets/Maps.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((User) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return display();
    }
}
