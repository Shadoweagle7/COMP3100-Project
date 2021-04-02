import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Client {
    public static final int MAX_BUFFER = 32;

    public static void printErrorMessage(String expected, String actual) {
        System.out.println("Expected: " + expected + " | Actual: " + actual);
    }

    public static void main(String[] args) throws Exception {
        // Using Java SE 7's Automatic Resource Management to call close() for us, since these objects implement the AutoCloseable interface.
        // This works even if any of the code throws an exception.
        try (
            Socket s = new Socket("localhost", 50000);
            DataInputStream din = new DataInputStream(s.getInputStream());
            DataOutputStream dout = new DataOutputStream(s.getOutputStream());
        ) {
            byte[] received = new byte[MAX_BUFFER];

            send(dout, "HELO");
        
            receive(received, din, "OK");

            send(dout, "AUTH bob");
            
            receive(received, din, "OK");

            send(dout, "REDY");

            receive(received, 32, din); // JOBN ...

            byte[] jobn = Arrays.copyOf(received, received.length); // Store for later

            send(dout, "GETS All");

            receive(received, 12, din); // DATA nRecs recLen

            DataCommand dataCommand = (DataCommand)parseCommand(new String(received));

            int[] dataBytes = dataCommand.execute();
            int totalDataSize = dataBytes[0] * dataBytes[1];

            ArrayList<String> servers = new ArrayList<String>();

            while (!(new String(received).equals("."))) {
                receive(received, totalDataSize, din);

                String serverStateAll = new String(received);
                String[] serverStates = serverStateAll.split("\n");

                for (String serverState : serverStates) {
                    servers.add(serverState);
                }
            }

            // TODO: LSTJ

            send(dout, "OK");

            send(dout, "QUIT");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void send(DataOutputStream dout, String toSend) throws IOException {
        dout.write(toSend.getBytes());
        dout.flush();
        System.out.println("Sending " + toSend);
    }

    public static void receive(byte[] data, int expectedSize, DataInputStream din) throws IOException {
        if (din.read(data) != expectedSize) {
            System.out.println("Server: " + new String(data, 0, expectedSize));
            throw new IllegalArgumentException("Server gave unexpected response");
        }

        System.out.println("Server: " + new String(data, 0, expectedSize));
    }

    public static void receive(byte[] data, DataInputStream din, String expectedString) throws IOException, IllegalArgumentException {
        int expectedSize = expectedString.length();

        if (din.read(data) != expectedSize) {
            System.out.println("Server: " + new String(data, 0, expectedSize));
            throw new IllegalArgumentException("Server gave unexpected response");
        }

        String receivedString = new String(data, 0, expectedSize);
        System.out.println("Server: " + receivedString);
        
        if (!receivedString.equals(expectedString)) {
            printErrorMessage(expectedString, receivedString);

            throw new IllegalArgumentException("Server sent unknown message");
        }
    }

    public static abstract class Command {
        public abstract Object execute();
    }

    public static class DataCommand extends Command {
        private int numberOfRecords, recordLength;

        public DataCommand(int nRec, int recLen) throws IllegalArgumentException {
            if (nRec <= 0 || recLen <= 0) {
                throw new IllegalArgumentException("Cannot have 0 or less records. Each record's size must be greater than 0");
            }

            this.numberOfRecords = nRec;
            this.recordLength = recLen;
        }

        public int[] execute() { // Covariant return allowed from base class Command. All arrays inherit from Object
            return new int[]{numberOfRecords, recordLength};
        }
    }

    public static Command parseCommand(String str) 
        throws NumberFormatException, ArrayIndexOutOfBoundsException, IllegalArgumentException, NullPointerException {
        if (str == null || str.length() == 0) {
            throw new NullPointerException("Invalid input");
        }

        String[] args = str.split(" ");

        if (args == null || args.length == 0) {
            throw new ArrayIndexOutOfBoundsException("Invalid command");
        }

        if (args[0].equals("DATA")) {
            int numberOfRecords = Integer.parseInt(args[1]), recordLength = Integer.parseInt(args[2]);

            return new DataCommand(numberOfRecords, recordLength);
        }

        throw new IllegalArgumentException("Invalid command");
    }
}