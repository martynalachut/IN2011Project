package webserver;

import in2011.http.Request;
import in2011.http.Response;
import in2011.http.StatusCodes;
import in2011.http.EmptyMessageException;
import in2011.http.MessageFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import org.apache.http.client.utils.DateUtils;

public class WebServer {

    private int port;
    private String rootDir;
    private OutputStream os;

    public WebServer(int port, String rootDir) {
        this.port = port;
        this.rootDir = rootDir;
    }

    public void start() throws IOException {
        
        ServerSocket serverSocket = new ServerSocket(port);
        while (true)
        {
            Socket conn = serverSocket.accept();
            InputStream is = conn.getInputStream();
            
            
            try 
            {
               os = conn.getOutputStream();
               Request request = Request.parse(is);
               
                if("GET".equals(request.getMethod()))
                {                    
                    String myURI = request.getURI();
                    String ps = rootDir + myURI;
                    Path myPath = Paths.get(ps).toAbsolutePath().normalize();
                    is = Files.newInputStream(myPath);
                    
                    byte[] buffer = new byte[1024];
                    int length = is.read(buffer);
                    
                    while(length != -1)
                    {
                        os.write(buffer, 0, length);
                        length = is.read(buffer);
                    }
                }
                else
                {
                    Response notImplMsg = new Response(501);
                    notImplMsg.write(os);
                    os.write("The method needed for the request has not been implemented \n".getBytes());
                }
            }
            catch (MessageFormatException msgFormatEx)
            {
                Response badMsg = new Response(400);
                badMsg.write(os);
                os.write("Bad request \n".getBytes());            
            }
            catch (NoSuchFileException noFileEx)
            {
                Response notFoundMsg = new Response(404);
                notFoundMsg.write(os);
                os.write("The file could not be found \n".getBytes());
            }            
            conn.close();
        }
    }

    public static void main(String[] args) throws IOException {
        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2) {
            throw new Error(usage);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Error(usage + "\n" + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        WebServer server = new WebServer(port, rootDir);
        server.start();
    }
}
