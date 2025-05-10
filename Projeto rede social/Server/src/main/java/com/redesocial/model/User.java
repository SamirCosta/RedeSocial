package com.redesocial.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private String password;
    private final LocalDateTime createdAt;
    private Set<String> followers;
    private Set<String> following;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.followers = new HashSet<>();
        this.following = new HashSet<>();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<String> getFollowers() {
        return new HashSet<>(followers);
    }

    public void addFollower(String username) {
        this.followers.add(username);
    }

    public void removeFollower(String username) {
        this.followers.remove(username);
    }

    public Set<String> getFollowing() {
        return new HashSet<>(following);
    }

    public void addFollowing(String username) {
        this.following.add(username);
    }

    public void removeFollowing(String username) {
        this.following.remove(username);
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", createdAt=" + createdAt +
                ", followers=" + followers.size() +
                ", following=" + following.size() +
                '}';
    }
}