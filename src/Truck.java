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

    private static int getGeneralId() {
        truckId++;
        return truckId;
    }

    public int getId() {
        return id;
    }
}