package com.ups.repository;

import com.ups.model.entity.PackageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageItemRepository extends JpaRepository<PackageItem, Long> {
    List<PackageItem> findByPkgId(Long packageId);
}