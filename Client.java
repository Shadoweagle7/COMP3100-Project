import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Client {
    public static void printErrorMessage(String expected, String actual) {
        System.out.println("Expected: " + expected + " | Actual: " + actual);
    }

    public static void printErrorMessage(int expected, int actual) {
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
            ArrayList<Job> jobs = new ArrayList<Job>();
            
            byte[] received = new byte[32];

            send(dout, "HELO");
        
            receive(received, "OK", din);

            send(dout, "AUTH bob");
            
            receive(received, "OK", din);

            send(dout, "REDY");

            String job = receive(received, 64, din); // JOBN ...

            jobs.add(new Job(job));

            //byte[] jobn = Arrays.copyOf(received, received.length); // Store for later

            send(dout, "GETS All");

            String rawDataString = receive(received, 12, din); // DATA nRecs recLen

            Data dataCommand = (Data)parseCommand(rawDataString);

            int[] dataBytes = dataCommand.execute();
            int totalDataSize = dataBytes[0] * dataBytes[1];

            send(dout, "OK");

            received = new byte[totalDataSize];
            String serversString = receive(received, totalDataSize, din);

            send(dout, "OK");

            receive(received, ".", din);

            //System.out.println(new String(received));

            // The magic infinite debug loop
            // int i = 0; while (i < Integer.MAX_VALUE) { i++; }

            // Build servers array
            ArrayList<Server> servers = new ArrayList<Server>();
            String serverStateAll = new String(serversString);
            String[] serverStates = serverStateAll.split("\n");

            for (String serverState : serverStates) {
                servers.add(new Server(serverState));
            }

            // Filter servers
            servers.removeIf((server) -> {
                return !(server.getState().equals(Server.STATE_IDLE) || server.getState().equals(Server.STATE_INACTIVE));
            });

            // Sort servers
            servers.sort((Server l, Server r) -> {
                if (l.getNumberOfCores() > r.getNumberOfCores()) {
                    return -1;
                } else if (l.getNumberOfCores() < r.getNumberOfCores()) {
                    return 1;
                }

                return 0;
            });

            Job current = jobs.get(0);
            
            while (!current.getType().equals("NONE")) {
                if (current.getType().equals("JOBN")) {
                    String sType = "lol";
                    int sID = 0;
                    
                    final int coreCount = current.getCore();
                    ArrayList<Server> compatibleServers = (ArrayList<Server>)servers.stream().filter(
                        (server) -> (server.getNumberOfCores() >= coreCount)
                    ).collect(Collectors.toList());

                    int minJobs = 1;
                    
                    for (int i = 0; i < compatibleServers.size(); i++) {
                        send(dout, "CNTJ " + compatibleServers.get(i).getServerType() + " " + compatibleServers.get(i).getServerID() + " " + 2 /* the running job state */ );
                        System.out.println(compatibleServers.get(i).getNumberOfCores());
                        int cntj = Integer.parseInt(receive(received, 4, din));

                        if (cntj < minJobs) {
                            sType = compatibleServers.get(i).getServerType();
                            sID = compatibleServers.get(i).getServerID();
                            break;
                        } else {
                            minJobs = cntj;
                        }
                    }

                    if (sType.equals("lol")) {
                        sType = compatibleServers.get(0).getServerType();
                        sID = compatibleServers.get(0).getServerID();
                    }

                    send(dout, "SCHD " + current.getJobID() + " " + sType + " " + sID);

                    receive(received, 64, din);

                    send(dout, "REDY");
                } else if (current.getType().equals("JCPL")) {
                    send(dout, "REDY");
                }

                current = new Job(receive(received, 64, din));
            }
            
            send(dout, "OK");

            send(dout, "QUIT");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void send(DataOutputStream dout, String toSend) throws IOException {
        System.out.println("Sending " + toSend);
        dout.write(toSend.getBytes());
        dout.flush();
    }

    public static String receive(byte[] data, int expectedSize, DataInputStream din) throws IOException {
        data = new byte[expectedSize];
        if (din.read(data) >= expectedSize) {
            printErrorMessage(expectedSize, data.length);
            //System.out.println("Server: " + new String(data, 0, expectedSize));
            //throw new IllegalArgumentException("Server gave unexpected response");
        }

        System.out.println("Server: " + new String(data));

        return new String(data).trim();
    }

    public static String receive(byte[] data, String expectedString, DataInputStream din) throws IOException, IllegalArgumentException {
        int expectedSize = expectedString.length();
        data = new byte[expectedSize];

        if (din.read(data) >= expectedSize) {
            //System.out.println("Server: " + new String(data, 0, expectedSize));
            //throw new IllegalArgumentException("Server gave unexpected response");
        }

        String receivedString = new String(data, 0, expectedSize).trim();
        System.out.println("Server: " + receivedString);
        
        if (!receivedString.equals(expectedString)) {
            printErrorMessage(expectedString, receivedString);

            throw new IllegalArgumentException("Server sent unknown message");
        }

        return receivedString;
    }

    public static class Server {
        public static final String STATE_ACTIVE = "active";
        public static final String STATE_INACTIVE = "inactive";
        public static final String STATE_IDLE = "idle";

        // serverType serverID state curStartTime core mem disk #wJobs #rJobs [#failures totalFailtime mttf mttr madf lastStartTime]
        // e.g. joon 1 inactive -1 4 16000 64000 0 0

        private String serverType;
        private int serverID;
        private String state;
        private int curStartTime;
        private int cores;
        private int memory;
        private int disk;
        private int wJobs;
        private int rJobs;

        public Server(String serverState) {
            String[] temp = serverState.split(" ");

            this.serverType = temp[0];
            this.serverID = Integer.parseInt(temp[1]);
            this.state = temp[2];

            this.cores = Integer.parseInt(temp[4]);
            this.rJobs = Integer.parseInt(temp[8]);
        }

        public String getServerType() {
            return this.serverType;
        }

        public int getServerID() {
            return this.serverID;
        }

        public String getState() {
            return this.state;
        }

        public int getNumberOfCores() {
            return this.cores;
        }

        public void printServer() {
            System.out.println(this.serverType + " " + this.serverID + " " + this.state + " " + this.cores);
        }
    }

    public static class Job {
        // JOBN submitTime jobID estRuntime core memory disk
        
        private String type;
        private int submitTime;
        private int jobID;
        private int estRunTime;
        private int core;
        private int memory;
        private int disk;
                
        public Job(String state) {
            String[] temp = state.split(" ");
            
            this.type = temp[0];
            if (this.type.equals("JOBN")) {
                this.submitTime = Integer.parseInt(temp[1]);
                this.jobID = Integer.parseInt(temp[2]);
                this.estRunTime = Integer.parseInt(temp[3]);
                this.core = Integer.parseInt(temp[4]);
                this.memory = Integer.parseInt(temp[5]);
                this.disk = Integer.parseInt(temp[6]);
            }
        }

        public int getJobID() {
            return this.jobID;
        }

        public String getType() {
            return this.type;
        }

        public int getCore() {
            return this.core;
        }
    }

    public static class Data {
        private int numberOfRecords, recordLength;

        public Data(int nRec, int recLen) throws IllegalArgumentException {
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

    public static Data parseCommand(String str) 
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

            return new Data(numberOfRecords, recordLength);
        }

        throw new IllegalArgumentException("Invalid command");
    }
}