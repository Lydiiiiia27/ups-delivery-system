package com.ups.model.entity;

public enum PackageStatus {
    CREATED,         // Initial state when created from Amazon request
    PACKING,         // Being packed at warehouse
    PACKED,          // Packed and ready for pickup
    ASSIGNED,        // Assigned to a truck but not yet picked up
    PICKUP_READY,    // Package ready for pickup at warehouse
    LOADING,         // Being loaded onto truck
    LOADED,          // Loaded on truck
    OUT_FOR_DELIVERY, // Truck has left warehouse and is on the way
    DELIVERING,      // In the process of being delivered
    DELIVERED,       // Successfully delivered
    FAILED           // Delivery failed
}