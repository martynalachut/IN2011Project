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
import java.util.Date;
import org.apache.http.client.utils.DateUtils;

final class MultiThreadedServer implements Runnable
{
    private int port;
    private String rootDir;
    private OutputStream os;
    private Thread runningThread;
    private boolean stopServer;
    private Path path;
    
    public MultiThreadedServer(int port, String rootDir)
    {
        this.port = port;
        this.rootDir = rootDir;
        this.runningThread = null;
        this.stopServer = false;
    }
    
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
                addIfModified(request);
                
                if("GET".equals(request.getMethod()))
                {
                    if (request.getVersion().equals("1.1"))
                    {
                        String myURI = request.getURI();
                        String ps = rootDir + myURI;
                        Path myPath = Paths.get(ps).toAbsolutePath().normalize();
                        this.path = myPath;
                        
                        /*if (myURI.contains("..") || myURI.contains("."))
                        {
                            Response forbidMsg = new Response (403);
                            forbidMsg.write(os);
                            os.write("Forbidden access ".getBytes());
                        }*/
                        
                        is = Files.newInputStream(myPath);
                        
                        byte[] buffer = new byte[1024];
                        int length = is.read(buffer);
                        
                        while(length != -1)
                        {
                            os.write(buffer, 0, length);
                            length = is.read(buffer);
                        }
                        if (getLatestModificationDate().compareTo(request.getHeaderFieldValue("If-Modified-Since")) > 0 )
                        {
                            Response notModified = new Response(304);
                            notModified.write(os);
                            os.write("The requested file has not been modified\n".getBytes());
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
    
    public void addIfModified(Request request)
    {
        //automatically add If-Modified header to every request 
        Date today = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        Date yesterday = calendar.getTime();
        String yesterdayDate = DateUtils.formatDate(yesterday, DateUtils.PATTERN_RFC1123);
        request.addHeaderField("If-Modified-Since", yesterdayDate);
    }
    public String getLatestModificationDate() throws IOException
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Files.getLastModifiedTime(this.path).toMillis());
        return DateUtils.formatDate(calendar.getTime(), DateUtils.PATTERN_RFC1123);
    }
    public boolean isModifiedRequired(Request request) throws IOException
    {
        if(request.getHeaderFieldValue("If-Modified-Since") != null)
        {
            return DateUtils.parseDate(getLatestModificationDate()).after(DateUtils.parseDate(request.getHeaderFieldValue("If-Modified-Since")));            
        }
        return true;
    }
    public void setModified() throws IOException
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        os.write(("Last-modified: " + getLatestModificationDate()).getBytes());
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
        //MultiThreadedServer server = new MultiThreadedServer(port, rootDir);
        Thread thread = new Thread(new MultiThreadedServer(port, rootDir));
        thread.start();
        //server.start();
    }
}
