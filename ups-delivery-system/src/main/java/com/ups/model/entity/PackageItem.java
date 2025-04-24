package com.ups.model.entity;

import jakarta.persistence.*;

@Entity
public class PackageItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "package_id")
    private Package pkg;
    
    private Long productId;
    private String description;
    private Integer count;
    
    // Constructors
    public PackageItem() {}
    
    public PackageItem(Long productId, String description, Integer count) {
        this.productId = productId;
        this.description = description;
        this.count = count;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Package getPkg() {
        return pkg;
    }
    
    public void setPkg(Package pkg) {
        this.pkg = pkg;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getCount() {
        return count;
    }
    
    public void setCount(Integer count) {
        this.count = count;
    }
}