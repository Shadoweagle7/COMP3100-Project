import java.io.*;
import java.net.*;

public class Client {
    static DataInputStream din;
    static DataOutputStream dout;
    static byte[] received;

    public static void main(String[] args) throws Exception {
        Socket s = new Socket("localhost", 50000);

        din = new DataInputStream(s.getInputStream());
        dout = new DataOutputStream(s.getOutputStream());
        
        send("HELO");
        
        receive(2, "OK");

        send("AUTH bob");
        
        receive(2, "OK");

        send("GETS ALL");
        
        din.read(received);
        // If str.equals("SOME VARIETY OF JOB")
        System.out.println("server: " + new String(received));

        send("QUIT");

        dout.close();
        din.close();
        s.close();
    }

    public static void send(String toSend) throws IOException {
        dout.write(toSend.getBytes());
        System.out.println("Sending " + toSend);
    }

    public static void receive(int size) throws IOException {
        received = new byte[size];
        din.read(received);
        System.out.println("Server: " + new String(received));
    }

    public static void receive(int size, String expectedString) throws IOException {
        received = new byte[size];
        din.read(received);

        String receivedString = new String(received);
        System.out.println("Server: " + receivedString);
        
        if (!receivedString.equals(expectedString)) {
            System.exit(0);
        }
    }
}