public class Truck {
    private static int truckId = 0;
    private int id;
    private Location location;
    private TruckStatus status;

    public Truck(int x, int y) {
        this.id = getGeneralId();
        this.location = new Location(x, y);
        this.status = TruckStatus.IDLE;
    }

    public Truck(Location location) {
        this.id = getGeneralId();
        this.location = new Location(location.getX(), location.getY());
        this.status = TruckStatus.IDLE;
    }

    private static int getGeneralId() {
        truckId++;
        return truckId;
    }

    public int getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }
}