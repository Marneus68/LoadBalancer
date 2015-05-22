import interfaces.pools.esgi.com.IPool;
import pools.esgi.com.Pool;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by madalien on 21/05/15.
 */
public class RequestHandler implements Runnable {

    Socket server = null;
    Socket client = null;
    BufferedReader pushBackedHeaderStream = null;

    public RequestHandler(Socket serverSocket, Socket clientSocket, BufferedReader clientStreamReader){
        server = serverSocket;
        client = clientSocket;
        pushBackedHeaderStream = clientStreamReader;
    }

    @Override
    public void run() {
        OutputStream streamToserver = null;
        try {
            streamToserver = server.getOutputStream();

            int bytesRead = 0;
            while (pushBackedHeaderStream.ready()) {
                char[] readbuf = new char[1024];
                pushBackedHeaderStream.read(readbuf);
                System.out.println("stream from client " + bytesRead+" cahr read"+readbuf.length);
                streamToserver.write(new String(readbuf).getBytes(), 0, readbuf.length);
                streamToserver.flush();
            }
        } catch (Exception e) {
            System.out.println(" Exception in thread execution stream "+ e.getMessage());
        }
    }
}
