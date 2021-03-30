import java.io.*;
import java.net.*;

public class Client {
    static BufferedReader din;
    static PrintStream dout;

    public static void main(String[] args) throws Exception {
        Socket s = new Socket("localhost", 50000);

        din = new BufferedReader(new InputStreamReader(s.getInputStream()));
        dout = new PrintStream(s.getOutputStream());
        String str="";
        
        dout.print("HELO\n");
        System.out.println("Sending HELO");
        
        str = waitForServer();
        if (!str.equals("OK")) {
            System.exit(0);
        }
        System.out.println("server: " + str);

        dout.print("AUTH bob\n");
        System.out.println("Authorising");
        
        str = waitForServer();
        if (!str.equals("OK")) {
            System.exit(0);
        }
        System.out.println("server: " + str);

        dout.print("REDY\n");
        System.out.println("Sending REDY");
        
        str = waitForServer();
        // If str.equals("SOME VARIETY OF JOB")
        System.out.println("server: " + str);

        dout.print("QUIT\n");
        System.out.println("Sending QUIT");

        dout.close();
        din.close();
        s.close();
    }

    public static String waitForServer() throws IOException {
        while (!Thread.interrupted()) {
            if (din.ready()) {
                return din.readLine();
            }
        }
        return "";
    }
}