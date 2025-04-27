package com.ups.model;

import java.util.Objects;

/**
 * Represents a location in the world with X and Y coordinates
 */
public class Location {
    private final int x;
    private final int y;
    
    /**
     * Create a new location
     * @param x The X coordinate
     * @param y The Y coordinate
     */
    public Location(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Get the X coordinate
     * @return The X coordinate
     */
    public int getX() {
        return x;
    }
    
    /**
     * Get the Y coordinate
     * @return The Y coordinate
     */
    public int getY() {
        return y;
    }
    
    /**
     * Calculate the Euclidean distance to another location
     * @param other The other location
     * @return The distance
     */
    public double distanceTo(Location other) {
        return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2));
    }
    
    /**
     * Calculate the Manhattan distance to another location
     * @param other The other location
     * @return The Manhattan distance
     */
    public int manhattanDistanceTo(Location other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return x == location.x && y == location.y;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
    
    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}