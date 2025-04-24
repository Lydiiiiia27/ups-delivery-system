import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

public class Ups {
    WorldConnector worldConnector;
    List<Truck> trucks;

    public Ups(String host, int port, int truckNum, Location initLoc) throws IOException {
        this.trucks = new ArrayList<>();
        for (int i = 0; i < truckNum; i++) {
            trucks.add(new Truck(initLoc));
        }
        this.worldConnector = new WorldConnector(host, port, trucks);

        worldConnector.deliver(1, 1, new Location(10, 10), 100);
        worldConnector.pickup(1, 1, 101);
        worldConnector.query(1, 102);
        worldConnector.query(2, 103);
        worldConnector.query(3, 104);
    }

}