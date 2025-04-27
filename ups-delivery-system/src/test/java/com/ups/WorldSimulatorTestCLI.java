package com.ups.test;

import com.ups.model.Location;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.Warehouse;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.repository.WarehouseRepository;
import com.ups.service.world.Ups;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

import java.util.Scanner;

@SpringBootApplication
@ComponentScan(basePackages = "com.ups")
@Profile("test-cli")
public class WorldSimulatorTestCLI implements CommandLineRunner {
    
    @Autowired
    private Ups ups;
    
    @Autowired
    private TruckRepository truckRepository;
    
    @Autowired
    private PackageRepository packageRepository;
    
    @Autowired
    private WarehouseRepository warehouseRepository;
    
    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test-cli");
        // Set a different server port to avoid conflicts
        System.setProperty("server.port", "8081");
        // Set local simulator hostname (for testing outside Docker)
        System.setProperty("ups.world.host", "localhost");
        
        SpringApplication.run(WorldSimulatorTestCLI.class, args);
    }
    
    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        System.out.println("World Simulator Test CLI");
        System.out.println("======================");
        
        // Check if we're connected to a world before showing the world ID
        Long worldId = ups.getWorldId();
        if (worldId != null) {
            System.out.println("Connected to World ID: " + worldId);
        } else {
            System.out.println("Not connected to a world. Some commands may not work.");
            System.out.println("You can manually set up trucks without world connection.");
        }
        
        while (running) {
            System.out.println("\nAvailable commands:");
            System.out.println("1. List trucks");
            System.out.println("2. Send truck to warehouse");
            System.out.println("3. Create test package");
            System.out.println("4. Send truck to deliver");
            System.out.println("5. Query truck status");
            System.out.println("6. List packages");
            System.out.println("7. Create test warehouse");
            System.out.println("8. Exit");
            System.out.print("\nEnter command (1-8): ");
            
            String command = scanner.nextLine();
            
            try {
                switch (command) {
                    case "1":
                        listTrucks();
                        break;
                    case "2":
                        sendTruckToWarehouse(scanner);
                        break;
                    case "3":
                        createTestPackage(scanner);
                        break;
                    case "4":
                        sendTruckToDeliver(scanner);
                        break;
                    case "5":
                        queryTruckStatus(scanner);
                        break;
                    case "6":
                        listPackages();
                        break;
                    case "7":
                        createTestWarehouse(scanner);
                        break;
                    case "8":
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid command");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Exiting...");
    }
    
    private void listTrucks() {
        System.out.println("\nTrucks:");
        truckRepository.findAll().forEach(truck -> {
            System.out.printf("ID: %d, Status: %s, Location: (%d,%d)\n",
                    truck.getId(), truck.getStatus(), truck.getX(), truck.getY());
        });
    }
    
    private void sendTruckToWarehouse(Scanner scanner) {
        System.out.print("Enter truck ID: ");
        int truckId = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter warehouse ID: ");
        int warehouseId = Integer.parseInt(scanner.nextLine());
        
        ups.sendTruckToPickup(truckId, warehouseId);
        System.out.println("Command sent to World Simulator");
    }
    
    private void createTestPackage(Scanner scanner) {
        System.out.print("Enter package ID: ");
        long packageId = Long.parseLong(scanner.nextLine());
        System.out.print("Enter warehouse ID: ");
        int warehouseId = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter destination X: ");
        int destX = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter destination Y: ");
        int destY = Integer.parseInt(scanner.nextLine());
        
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        
        Package pkg = new Package();
        pkg.setId(packageId);
        pkg.setWarehouse(warehouse);
        pkg.setStatus(PackageStatus.LOADED);
        pkg.setDestinationX(destX);
        pkg.setDestinationY(destY);
        packageRepository.save(pkg);
        
        System.out.println("Package created");
    }
    
    private void sendTruckToDeliver(Scanner scanner) {
        System.out.print("Enter truck ID: ");
        int truckId = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter package ID: ");
        long packageId = Long.parseLong(scanner.nextLine());
        
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new RuntimeException("Package not found"));
        
        Location destination = new Location(pkg.getDestinationX(), pkg.getDestinationY());
        ups.sendTruckToDeliver(truckId, packageId, destination);
        System.out.println("Command sent to World Simulator");
    }
    
    private void queryTruckStatus(Scanner scanner) {
        System.out.print("Enter truck ID: ");
        int truckId = Integer.parseInt(scanner.nextLine());
        
        ups.queryTruckStatus(truckId);
        System.out.println("Query sent to World Simulator");
    }
    
    private void listPackages() {
        System.out.println("\nPackages:");
        packageRepository.findAll().forEach(pkg -> {
            System.out.printf("ID: %d, Status: %s, Destination: (%d,%d)\n",
                    pkg.getId(), pkg.getStatus(), pkg.getDestinationX(), pkg.getDestinationY());
        });
    }
    
    private void createTestWarehouse(Scanner scanner) {
        System.out.print("Enter warehouse ID: ");
        int warehouseId = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter X coordinate: ");
        int x = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter Y coordinate: ");
        int y = Integer.parseInt(scanner.nextLine());
        
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setX(x);
        warehouse.setY(y);
        warehouseRepository.save(warehouse);
        
        System.out.println("Warehouse created");
    }
}