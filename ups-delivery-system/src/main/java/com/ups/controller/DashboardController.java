package com.ups.controller;

import com.ups.model.entity.Package;
import com.ups.model.entity.User;
import com.ups.repository.PackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ups.model.entity.PackageStatus;
import com.ups.service.UserService;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    
    private final PackageRepository packageRepository;
    private final UserService userService;
    
    @Autowired
    public DashboardController(PackageRepository packageRepository, UserService userService) {
        this.packageRepository = packageRepository;
        this.userService = userService;
    }
    
    @GetMapping
    public String showDashboard(Model model) {
        // Get current user
        String username = getCurrentUsername();
        Optional<User> userOpt = userService.findByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            List<Package> packages = packageRepository.findByUserId(user.getId());
            
            model.addAttribute("user", user);
            model.addAttribute("packages", packages);
            return "dashboard";
        } else {
            return "redirect:/login";
        }
    }
    
    @GetMapping("/package/{id}")
    public String showPackageDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Package> packageOpt = packageRepository.findById(id);
        
        if (packageOpt.isPresent()) {
            Package pkg = packageOpt.get();
            
            // Check if package belongs to current user
            String username = getCurrentUsername();
            Optional<User> userOpt = userService.findByUsername(username);
            
            if (userOpt.isPresent() && pkg.getUser() != null && 
                    pkg.getUser().getId().equals(userOpt.get().getId())) {
                model.addAttribute("package", pkg);
                return "user-package-details";
            } else {
                redirectAttributes.addFlashAttribute("error", "You don't have permission to view this package");
                return "redirect:/dashboard";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Package not found");
            return "redirect:/dashboard";
        }
    }
    
    @PostMapping("/package/{id}/redirect")
    public String redirectPackage(
            @PathVariable Long id, 
            @RequestParam Integer destinationX, 
            @RequestParam Integer destinationY,
            RedirectAttributes redirectAttributes) {
        
        Optional<Package> packageOpt = packageRepository.findById(id);
        
        if (packageOpt.isPresent()) {
            Package pkg = packageOpt.get();
            
            // Check if package belongs to current user
            String username = getCurrentUsername();
            Optional<User> userOpt = userService.findByUsername(username);
            
            if (userOpt.isPresent() && pkg.getUser() != null && 
                    pkg.getUser().getId().equals(userOpt.get().getId())) {
                
                // Check if package can be redirected (not yet out for delivery)
                if (pkg.getStatus() == PackageStatus.DELIVERING || pkg.getStatus() == PackageStatus.DELIVERED) {
                    redirectAttributes.addFlashAttribute("error", "Package cannot be redirected as it is already out for delivery or delivered");
                    return "redirect:/dashboard/package/" + id;
                }
                
                // Update destination
                pkg.setDestinationX(destinationX);
                pkg.setDestinationY(destinationY);
                packageRepository.save(pkg);
                
                // TODO: Implement communication with Amazon to notify of the destination change
                
                redirectAttributes.addFlashAttribute("success", "Package destination updated successfully");
                return "redirect:/dashboard/package/" + id;
            } else {
                redirectAttributes.addFlashAttribute("error", "You don't have permission to modify this package");
                return "redirect:/dashboard";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Package not found");
            return "redirect:/dashboard";
        }
    }
    
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        
        return principal.toString();
    }
}