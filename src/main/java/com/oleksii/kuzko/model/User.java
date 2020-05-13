package com.oleksii.kuzko.model;

public class User {

    private final String login;
    private final String email;
    private final String name;
    private final String password;

    public User(String login, String email, String name, String password) {
        this.login = login;
        this.email = email;
        this.name = name;
        this.password = password;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }
}
