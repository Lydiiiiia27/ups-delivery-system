package com.ups.repository;

import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TruckRepository extends JpaRepository<Truck, Integer> {
    List<Truck> findByStatus(TruckStatus status);
}