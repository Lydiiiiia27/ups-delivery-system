package com.ups.service;

import com.ups.model.entity.PackageItem;
import com.ups.repository.PackageItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PackageItemService {
    
    private final PackageItemRepository packageItemRepository;
    
    @Autowired
    public PackageItemService(PackageItemRepository packageItemRepository) {
        this.packageItemRepository = packageItemRepository;
    }
    
    public List<PackageItem> getItemsByPackageId(Long packageId) {
        return packageItemRepository.findByPkgId(packageId);
    }
    
    public void saveItem(PackageItem item) {
        packageItemRepository.save(item);
    }
}