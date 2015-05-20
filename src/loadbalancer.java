import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * File created by duane
 * 2015-05-18 | 4:55 PM
 */

class Worker {
    public Boolean valid = true;
    String ip;
    String port;
    public Worker() {}
    public  Worker(String ip, String port) {
        this.ip = ip;
        this.port = port;
    }
    public String toString() {
        return ip + ":" + port;
    }
}

public class loadbalancer {
    public static final int PORT = 8181;

    static Map<String, HashMap<String, String>> workers = new HashMap<String, HashMap<String, String>>();
    static Map<String, HashMap<String, String>> lbs = new HashMap<String, HashMap<String, String>>();

    public static void main(String [] args) throws Exception {
        int port = PORT;

        if (args.length > 0)
            port = Integer.valueOf(args[0]);

        Properties props = new Properties();
        InputStream input = new FileInputStream("config.ini");
        props.load(input);
        for (String key : props.stringPropertyNames()) {
            String[] keys = key.split("\\.");

            if (keys[0].equals("worker")) {
                if (!workers.containsKey(keys[1]))
                    workers.put(keys[1], new HashMap<String, String>());
                workers.get(keys[1]).put(keys[2], props.getProperty(key));
            } else if (keys[0].equals("lb")) {
                if (!lbs.containsKey(keys[1]))
                    lbs.put(keys[1], new HashMap<String, String>());
                lbs.get(keys[1]).put(keys[2], props.getProperty(key));
            }
        }

        System.out.println("LoadBalancer started on port " + port);
        System.out.println("  " + workers.size() + " workers defined");
        System.out.println("  Loadbalancers configurations: " + lbs.size());

        ServerSocket ss = new ServerSocket(PORT);
        final byte[] request = new byte[1024];
        byte[] reply = new byte[4096];

        while (true) {
            Socket client = null, server = null;
            try {
                client = ss.accept();
                final InputStream streamFromClient = client.getInputStream();
                final OutputStream streamToClient = client.getOutputStream();
                final BufferedReader in = new BufferedReader(new InputStreamReader(streamFromClient));
                String domainName = "";
                String line = "";
                in.mark(1024);
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    System.out.println("readed line"+ line);
                    if (String.valueOf(line).startsWith("Host: ")) {
                        domainName = line.replace("Host: ", "").split(":")[0];
                        break;
                    }
                }
                in.reset();

                Worker w = getNextWorker(domainName);
                if (!w.valid)
                    continue;

                System.out.println(" -> Connection accepted, trying to reach " + w.ip + ":" + w.port);
                System.out.println("    Domain name: " + domainName);

                try {
                    server = new Socket(w.ip, Integer.valueOf(w.port));
                } catch (Exception e) {
                    System.err.println("    Cannot connect to " + w.ip + ":" + w.port);
                    continue;
                }

                final InputStream streamFromServer = server.getInputStream();
                final OutputStream streamToServer = server.getOutputStream();

                Thread t = new Thread() {
                    public void run() {
                        int bytesRead = 0;
                        try {
                            while (in.ready()) {
                                char[] readbuf = new char[1024];
                                in.read(readbuf);
                                System.out.println("stream from client " + bytesRead+" cahr read"+readbuf.length);
                                streamToServer.write(new String(readbuf).getBytes(), 0, readbuf.length);
                                streamToServer.flush();
                            }

                        } catch (Exception e) {
                            System.out.println(" Exception server in stream "+ e.getMessage());
                        }

                    }
                };

                t.start();

                int bytesRead = 0;
                try {
                    while ((bytesRead = streamFromServer.read(reply)) != -1) {
                        streamToClient.write(reply, 0, bytesRead);
                        streamToClient.flush();
                    }

                    streamToServer.close();
                } catch (Exception e) {

                    System.out.println(" server in stream"+ e.getMessage());

                }

                streamToClient.close();
            } catch (Exception e) {
                System.err.println(e);
            }
            finally {
                try {
                    if (server != null)
                        server.close();
                    if (client != null)
                        client.close();
                } catch (Exception e) {}
            }

        }
    }

    static int counter = 0;

    public static Worker getNextWorker(String domainName) throws Exception{
        Worker ret = new Worker();
        String[] wrks = {};
        String strategy = "";
        int found = 0;

        for(Map.Entry<String, HashMap<String, String>> entry : lbs.entrySet()) {
            if (entry.getValue().containsKey("domains")) {
                if (entry.getValue().get("domains").contains(domainName)) {
                    wrks = entry.getValue().get("workers").split(",");
                    strategy = entry.getValue().get("strategy");
                    found++;
                }
            }
        }

        if (found == 0) {
            System.err.println(" -> domain " + domainName + " isn't handled by the LoadBalancer");
            ret.valid = false;
        }

        if (strategy.equals("round_robin")) {
            ret = new Worker(workers.get(String.valueOf(counter % wrks.length)).get("ip"), workers.get(String.valueOf(counter % wrks.length)).get("port"));
            counter++;
        } else if (strategy.equals("sticky_session")) {

        }
        return ret;
    }
}
