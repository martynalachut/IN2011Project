package webserver;
import in2011.http.Request;
import in2011.http.Response;
import in2011.http.MessageFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import org.apache.http.client.utils.DateUtils;

/*
This is the coursework part 2 submission by Martyna Lachut, Vladyslav Atamanyuk
and Daniel Lugo Pino.

The advanced feature we've chosen to do is the 2.1 Support for Client Side Cach-
ing.
*/

final class MultiThreadedServer implements Runnable
{
    private int port;
    private String rootDir;
    private OutputStream os;
    private Thread runningThread;
    private boolean stopServer;
    private Path path;
    private int flag;
    
    public MultiThreadedServer(int port, String rootDir)
    {
        this.port = port;
        this.rootDir = rootDir;
        this.runningThread = null;
        this.stopServer = false;
        this.flag = 0;
    }
    
    //This method is in charge of the multi-threading of the HTTP Server.
    //It allows for multiple Get Requests be received at the same time, without
    // having to wait for a request to be processed.
    public void run()
    {
        synchronized(this)
        {
            this.runningThread = Thread.currentThread();
        }
        while(! stopServer)
        {
            try
            {
                start();
            }
            catch (Exception e)
            {
                System.out.println(e);
            } 
        }
    }
    
    public void start() throws IOException
    {
        
        ServerSocket serverSocket = new ServerSocket(port);
        while (true)
        {
            Socket conn = serverSocket.accept();
            InputStream is = conn.getInputStream();
            Thread thread = new Thread(new MultiThreadedServer(port, rootDir));
            
            try
            {
                os = conn.getOutputStream();
                Request request = Request.parse(is);
                
                if("GET".equals(request.getMethod()))
                {
                    if (request.getVersion().equals("1.1"))
                    {
                        String myURI = request.getURI();
                        String ps = rootDir + myURI;
                        Path myPath = Paths.get(ps).toAbsolutePath().normalize();
                        this.path = myPath;
                        
                        if(isHeaderValueNull(request) == false)
                        {
                            if(getLatestModificationTime().compareTo(request.getHeaderFieldValue("If-Modified-Since")) < 0);
                            {
                                Response notModified = new Response(304);
                                notModified.write(os);
                                os.write("The requested file has not been modified \n".getBytes());
                                flag = 1;
                            }
                        }
                        //don't run this code if 304
                        if(flag == 0)
                        {
                            //send OK HTTP response + last-modified header
                            Response okResponse = new Response(200);
                            addLastModified(okResponse);
                            okResponse.write(os);
                            os.write("Valid request \n".getBytes());

                            is = Files.newInputStream(myPath);
                           //Allocates the contents to a file (regardless of its type)
                           //to a byte array which can be read later.
                            byte[] buffer = new byte[1024];
                            int length = is.read(buffer);

                            while(length != -1)
                            {
                                os.write(buffer, 0, length);
                                length = is.read(buffer);
                            }
                        }
                    }
                    else
                    {
                        Response invalidVrs = new Response (505);
                        invalidVrs.write(os);
                        os.write("Requested http version is not supported \n".getBytes());
                    }
                }
                else
                {
                    Response notImplMsg = new Response(501);
                    notImplMsg.write(os);
                    os.write("The method needed for the request has not been implemented \n".getBytes());
                }
            }
            catch (AccessDeniedException accessEx)
            {
                Response accessMsg = new Response(403);
                accessMsg.write(os);
                os.write("Insufficient privilages to access the folder \n".getBytes());
                
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
            catch (RuntimeException svrError)
            {
                Response svrErrorMsg = new Response(500);
                svrErrorMsg.write(os);
                os.write("Internal server error occurred \n".getBytes());
            }
            conn.close();
        }
    }
    
    public void addLastModified(Response response) throws IOException
    {
        response.addHeaderField("Last-Modified", getLatestModificationTime());
    }
    
    public boolean isHeaderValueNull(Request request)
    {
        if(request.getHeaderFieldValue("If-Modified-Since") == null)
        {
            return true;
        }
        return false;
    }
    
    public String getLatestModificationTime() throws IOException
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Files.getLastModifiedTime(this.path).toMillis());
        return DateUtils.formatDate(calendar.getTime(), DateUtils.PATTERN_RFC1123);
    }
}

public final class WebServer
{
    public static void main(String[] args) throws IOException
    {
        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2)
        {
            throw new Error(usage);
        }
        int port;
        try
        {
            port = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e)
        {
            throw new Error(usage + " " + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        Thread thread = new Thread(new MultiThreadedServer(port, rootDir));
        thread.start();
    }
}
