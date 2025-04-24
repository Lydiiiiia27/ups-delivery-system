package com.ups.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "packages") // Avoid using reserved word "package" as table name
public class Package {
    
    @Id
    private Long id; // This will be the packageId/shipId
    
    @ManyToOne
    @JoinColumn(name = "truck_id")
    private Truck truck;
    
    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    private Integer destinationX;
    private Integer destinationY;
    
    @Enumerated(EnumType.STRING)
    private PackageStatus status;
    
    @OneToMany(mappedBy = "pkg", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackageItem> items = new ArrayList<>();
    
    private Instant createdAt;
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Constructors, getters, and setters
    public Package() {}
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Truck getTruck() {
        return truck;
    }
    
    public void setTruck(Truck truck) {
        this.truck = truck;
    }
    
    public Warehouse getWarehouse() {
        return warehouse;
    }
    
    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public Integer getDestinationX() {
        return destinationX;
    }
    
    public void setDestinationX(Integer destinationX) {
        this.destinationX = destinationX;
    }
    
    public Integer getDestinationY() {
        return destinationY;
    }
    
    public void setDestinationY(Integer destinationY) {
        this.destinationY = destinationY;
    }
    
    public PackageStatus getStatus() {
        return status;
    }
    
    public void setStatus(PackageStatus status) {
        this.status = status;
    }
    
    public List<PackageItem> getItems() {
        return items;
    }
    
    public void setItems(List<PackageItem> items) {
        this.items = items;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Helper methods
    public void addItem(PackageItem item) {
        items.add(item);
        item.setPkg(this);
    }
    
    public void removeItem(PackageItem item) {
        items.remove(item);
        item.setPkg(null);
    }
}