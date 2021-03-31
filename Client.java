import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Client {
    static DataInputStream din;
    static DataOutputStream dout;
    static byte[] received;

    public static void printErrorMessage(String expected, String actual) {
        System.out.println("Expected: " + expected + " | Actual: " + actual);
    }

    public static void main(String[] args) throws Exception {
        Socket s = new Socket("localhost", 50000);

        din = new DataInputStream(s.getInputStream());
        dout = new DataOutputStream(s.getOutputStream());

        send("HELO");
        
        receive(2, "OK");

        send("AUTH bob");
        
        receive(2, "OK");

        send("REDY");

        receive(32); // JOBN ...

        byte[] jobn = Arrays.copyOf(received, received.length); // Store for later

        send("GETS All");

        receive(12); // DATA nRecs recLen

        DataCommand dataCommand = (DataCommand)parseCommand(new String(received));

        int[] dataBytes = dataCommand.execute();
        int totalDataSize = dataBytes[0] * dataBytes[1];

        ArrayList<String> servers = new ArrayList<String>();

        while (!(new String(received).equals("."))) {
            receive(totalDataSize);

            String serverStateAll = new String(received);
            String[] serverStates = serverStateAll.split("\n");

            for (String serverState : serverStates) {
                servers.add(serverState);
            }
        }

        // TODO: LSTJ

        send("OK");

        send("QUIT");

        dout.close();
        din.close();
        s.close();
    }

    public static void send(String toSend) throws IOException {
        dout.write(toSend.getBytes());
        dout.flush();
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
            printErrorMessage(expectedString, receivedString);

            // TODO: throw
        }
    }

    public static abstract class Command {
        public abstract Object execute();
    }

    public static class DataCommand extends Command {
        private int numberOfRecords, recordLength;

        public DataCommand(int nRec, int recLen) {
            this.numberOfRecords = nRec;
            this.recordLength = recLen;
        }

        public int[] execute() { // Covariant return allowed from base class Command. All arrays inherit from Object
            return new int[]{numberOfRecords, recordLength};
        }
    }

    public static Command parseCommand(String str) throws NumberFormatException, ArrayIndexOutOfBoundsException, IllegalArgumentException {
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