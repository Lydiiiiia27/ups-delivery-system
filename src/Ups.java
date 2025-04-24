import java.util.List;
import java.io.IOException;

public class Ups {
    WorldConnector worldConnector;
    List<Truck> trucks;

    public Ups(String host, int port, List<Truck> trucks) throws IOException {
        this.worldConnector = new WorldConnector(host, port);
        this.trucks = trucks;

        worldConnector.deliver(1, 1, new Location(10, 10), 100);
        worldConnector.pickup(1, 1, 101);
        worldConnector.query(1, 102);
    }

}