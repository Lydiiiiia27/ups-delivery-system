package com.ups.repository;

import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageRepository extends JpaRepository<Package, Long> {
    List<Package> findByStatus(PackageStatus status);
    List<Package> findByUserId(Long userId);
    List<Package> findByWarehouseId(Integer warehouseId);
    List<Package> findByTruckId(Integer truckId);
    
    // New methods needed for the WorldResponseHandler
    List<Package> findByTruck(Truck truck);
    List<Package> findByTruckAndStatus(Truck truck, PackageStatus status);
}