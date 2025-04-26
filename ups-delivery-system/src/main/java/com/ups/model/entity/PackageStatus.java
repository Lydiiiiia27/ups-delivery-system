package com.ups.model.entity;

public enum PackageStatus {
    CREATED,   // Initial state when created from Amazon request
    PACKING,   // Being packed at warehouse
    PACKED,    // Packed and ready for pickup
    LOADING,   // Being loaded onto truck
    LOADED,    // Loaded on truck
    DELIVERING, // Out for delivery
    DELIVERED   // Successfully delivered
}