package com.ups.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Warehouse {
    
    @Id
    private Integer id;
    
    private Integer x;
    private Integer y;
    
    @OneToMany(mappedBy = "warehouse")
    private Set<Package> packages = new HashSet<>();
    
    // Constructors
    public Warehouse() {}
    
    public Warehouse(Integer id, Integer x, Integer y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Integer getX() {
        return x;
    }
    
    public void setX(Integer x) {
        this.x = x;
    }
    
    public Integer getY() {
        return y;
    }
    
    public void setY(Integer y) {
        this.y = y;
    }
    
    public Set<Package> getPackages() {
        return packages;
    }
    
    public void setPackages(Set<Package> packages) {
        this.packages = packages;
    }
    
    // Helper methods
    public void addPackage(Package pkg) {
        packages.add(pkg);
        pkg.setWarehouse(this);
    }
    
    public void removePackage(Package pkg) {
        packages.remove(pkg);
        pkg.setWarehouse(null);
    }
}