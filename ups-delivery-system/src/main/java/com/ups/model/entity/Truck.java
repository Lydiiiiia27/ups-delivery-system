package com.ups.model.entity;

import com.ups.model.Location;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Truck {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    
    private Integer x;
    private Integer y;
    
    @Enumerated(EnumType.STRING)
    private TruckStatus status;
    
    @OneToMany(mappedBy = "truck")
    private Set<Package> packages = new HashSet<>();
    
    // Constructors
    public Truck() {
        this.status = TruckStatus.IDLE;
    }
    
    public Truck(Integer x, Integer y) {
        this.x = x;
        this.y = y;
        this.status = TruckStatus.IDLE;
    }
    
    public Truck(Location location) {
        this.x = location.getX();
        this.y = location.getY();
        this.status = TruckStatus.IDLE;
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
    
    public Location getLocation() {
        return new Location(x, y);
    }
    
    public void setLocation(Location location) {
        this.x = location.getX();
        this.y = location.getY();
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