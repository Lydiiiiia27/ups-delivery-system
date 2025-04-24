package com.ups.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Truck {
    
    @Id
    private Integer id;
    
    private Integer x;
    private Integer y;
    
    @Enumerated(EnumType.STRING)
    private TruckStatus status;
    
    @OneToMany(mappedBy = "truck")
    private Set<Package> packages = new HashSet<>();
    
    // Constructors
    public Truck() {}
    
    public Truck(Integer id, Integer x, Integer y, TruckStatus status) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.status = status;
    }
    
    // Getters and setters
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
    
    public TruckStatus getStatus() {
        return status;
    }
    
    public void setStatus(TruckStatus status) {
        this.status = status;
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
        pkg.setTruck(this);
    }
    
    public void removePackage(Package pkg) {
        packages.remove(pkg);
        pkg.setTruck(null);
    }
}