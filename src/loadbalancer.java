import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * File created by duane
 * 2015-05-18 | 4:55 PM
 */

class Worker {
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

        Properties props = new Properties();
        InputStream input = new FileInputStream("config.ini");
        props.load(input);
        for (String key : props.stringPropertyNames()) {
            String[] keys = key.split("\\.");

            if (keys[0].equals("worker")) {
                if (!workers.containsKey(keys[1]))
                    workers.put(keys[1], new HashMap<String, String>());

                if (keys[2].equals("port")) {
                    workers.get(keys[1]).put("port", props.getProperty(key));
                } else if (keys[2].equals("ip")) {
                    workers.get(keys[1]).put("ip", props.getProperty(key));
                }
            } else if (keys[0].equals("lb")) {
                if (!lbs.containsKey(keys[1]))
                    lbs.put(keys[1], new HashMap<String, String>());

                if (keys[2].equals("workers")) {
                    lbs.get(keys[1]).put("workers", props.getProperty(key));
                } else if (keys[2].equals("strategy")) {
                    lbs.get(keys[1]).put("strategy", props.getProperty(key));
                }
            }
        }

        System.out.println("LoadBalancer started.");
        System.out.println("  " + workers.size() + " workers defined");
        System.out.println("  Loadbalancers configurations: " + lbs.size());

        ServerSocket ss = new ServerSocket(PORT);
        final byte[] request = new byte[1024];
        byte[] reply = new byte[4096];

        while (true) {
            Worker w = getNextWorker("0");
            Socket client = null, server = null;
            try {
                client = ss.accept();
                System.out.println("    Connection accepted, trying to reach " + w.ip + ":" + w.port);
                final InputStream sfc = client.getInputStream();
                final OutputStream stc = client.getOutputStream();

                try {
                    server = new Socket(w.ip, Integer.valueOf(w.port));
                } catch (Exception e) {
                    System.err.println("    Cannot connect to " + w.ip + ":" + w.port);
                    //continue;
                }

                final InputStream sfs = server.getInputStream();
                final OutputStream sts = server.getOutputStream();

                Thread t = new Thread() {
                    public void run() {
                        int bytesRead;
                        try {
                            while ((bytesRead = sfc.read(request)) != -1) {
                                sts.write(request, 0, bytesRead);
                                sts.flush();
                            }
                        } catch (Exception e) {
                        }

                        try {
                            sts.close();
                        } catch (Exception e) {
                            System.err.println("Error socket out closing...");
                        }
                    }
                };

                t.start();

                int bytesRead;
                try {
                    while ((bytesRead = sfs.read(reply)) != -1) {
                        stc.write(reply, 0, bytesRead);
                        stc.flush();
                    }
                } catch (Exception e) {
                    System.err.println("Error client...");
                }

                stc.close();
            } catch (Exception e) {
                System.err.println(e);
            } finally {
                try {
                    if (server != null)
                        server.close();
                    if (client != null)
                        client.close();
                } catch (Exception e) {
                    System.err.println("Error socket server/client...");
                }
            }
        }
    }

    static int counter = 0;

    public static Worker getNextWorker(String lbstrategy) {
        Worker ret = new Worker();
        String[] wrks = lbs.get(lbstrategy).get("workers").split(",");
        String strategy = lbs.get(lbstrategy).get("strategy");

        if (strategy.equals("round_robin")) {
            ret = new Worker(workers.get(String.valueOf(counter % wrks.length)).get("ip"), workers.get(String.valueOf(counter % wrks.length)).get("port"));
            counter++;
        }
        return ret;
    }
}
