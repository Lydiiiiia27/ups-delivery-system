package com.ups.controller;

import com.ups.model.entity.Package;
import com.ups.model.entity.PackageItem;
import com.ups.repository.PackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/tracking")
public class TrackingController {
    
    private final PackageRepository packageRepository;
    
    @Autowired
    public TrackingController(PackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }
    
    @GetMapping
    public String showTrackingForm() {
        return "tracking";
    }
    
    @PostMapping
    public String trackPackage(@RequestParam("trackingNumber") Long trackingNumber, Model model) {
        Optional<Package> packageOpt = packageRepository.findById(trackingNumber);
        
        if (packageOpt.isPresent()) {
            Package pkg = packageOpt.get();
            model.addAttribute("package", pkg);
            return "package-details";
        } else {
            model.addAttribute("error", "Package with tracking number " + trackingNumber + " not found");
            return "tracking";
        }
    }
}