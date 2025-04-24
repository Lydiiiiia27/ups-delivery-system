package com.ups.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users") // Avoid using reserved word "user" as table name
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String username;
    
    private String password; // Store hashed password
    
    @Column(unique = true)
    private String email;
    
    @OneToMany(mappedBy = "user")
    private Set<Package> packages = new HashSet<>();
    
    private boolean enabled = true;
    
    // Constructors
    public User() {}
    
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Set<Package> getPackages() {
        return packages;
    }
    
    public void setPackages(Set<Package> packages) {
        this.packages = packages;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    // Helper methods
    public void addPackage(Package pkg) {
        packages.add(pkg);
        pkg.setUser(this);
    }
    
    public void removePackage(Package pkg) {
        packages.remove(pkg);
        pkg.setUser(null);
    }
}