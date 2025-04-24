import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        try {
            Ups ups = new Ups("localhost", 12345, 3, new Location(5, 5));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}