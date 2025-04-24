package com.ups.repository;

import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageRepository extends JpaRepository<Package, Long> {
    List<Package> findByStatus(PackageStatus status);
    List<Package> findByUserId(Long userId);
    List<Package> findByWarehouseId(Integer warehouseId);
    List<Package> findByTruckId(Integer truckId);
}