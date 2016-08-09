/* Webserver.java -- Matt Mahoney, mmahoney@cs.fit.edu

Webserver.java runs a simple web server.  To run, cd to the directory
containing the .html files to be served, and run:

  java Webserver >logfile

To kill the server, type Ctrl-C.

The server serves files only in the current directory and subdirectories.
It does not support CGI, cookies, authentication, keepalive, etc.
Client requests are logged to standard output.

Webserver accepts HTTP GET requests that begin with:

  GET /filename.html

If the file exists, it responds with:

  HTTP/1.0 200 OK\r\n
  Content-Type: text/html\r\n
  \r\n
  (contents of filename.html)

where \r\n is a carriage return and line feed.  The content type depends
on the filename extension as follows:

.html   text/html
.htm    text/html
.gif    image/gif
.jpeg   image/jpeg
.jpg    image/jpeg
.class  application/octet-stream
.*      text/plain  (all other extensions)

The server closes the connection after returning a file.  Keepalive
(the default in HTTP/1.1) is not implemented.

If the file does not exist or the request is not understood, the server
sends an HTTP "404 Not Found" error message back to the client.

If the client requests a directory without a trailing "/",
then the client is redirected to append it with an "HTTP 301 Moved
Permanently" response.  If the trailing slash is present, the server
returns the file index.html if it exists.

To prevent access outside the directory tree where the server was started,
file names containing "..", "|", or ":" are rejected.
*/

import java.util.*;
import java.io.*;
import java.net.*;

// A Webserver waits for clients to connect, then starts a separate
// thread to handle the request.
public class Webserver {
  private static ServerSocket serverSocket;

  public static void main(String[] args) throws IOException {
    serverSocket=new ServerSocket(80);  // Start, listen on port 80
    while (true) {
      try {
        Socket s=serverSocket.accept();  // Wait for a client to connect
        new ClientHandler(s);  // Handle the client in a separate thread
      }
      catch (Exception x) {
        System.out.println(x);
      }
    }
  }
}

// A ClientHandler reads an HTTP request and responds
class ClientHandler extends Thread {
  private Socket socket;  // The accepted socket from the Webserver

  // Start the thread in the constructor
  public ClientHandler(Socket s) {
    socket=s;
    start();
  }

  // Read the HTTP request, respond, and close the connection
  public void run() {
    try {

      // Open connections to the socket
      BufferedReader in=new BufferedReader(new InputStreamReader(
        socket.getInputStream()));
      PrintStream out=new PrintStream(new BufferedOutputStream(
        socket.getOutputStream()));

      // Read filename from first input line "GET /filename.html ..."
      // or if not in this format, treat as a file not found.
      String s=in.readLine();
      System.out.println(s);  // Log the request

      // Attempt to serve the file.  Catch FileNotFoundException and
      // return an HTTP error "404 Not Found".  Treat invalid requests
      // the same way.
      String filename="";
      StringTokenizer st=new StringTokenizer(s);
      try {

        // Parse the filename from the GET command
        if (st.hasMoreElements() && st.nextToken().equalsIgnoreCase("GET")
            && st.hasMoreElements())
          filename=st.nextToken();
        else
          throw new FileNotFoundException();  // Bad request

        // Append trailing "/" with "index.html"
        if (filename.endsWith("/"))
          filename+="index.html";

        // Remove leading / from filename
        while (filename.indexOf("/")==0)
          filename=filename.substring(1);

        // Replace "/" with "\" in path for PC-based servers
        filename=filename.replace('/', File.separator.charAt(0));

        // Check for illegal characters to prevent access to superdirectories
        if (filename.indexOf("..")>=0 || filename.indexOf(':')>=0
            || filename.indexOf('|')>=0)
          throw new FileNotFoundException();


        // If a directory is requested and the trailing / is missing,
        // send the client an HTTP request to append it.  (This is
        // necessary for relative links to work correctly in the client).
        if (new File(filename).isDirectory()) {
          filename=filename.replace('\\', '/');
          out.print("HTTP/1.0 301 Moved Permanently\r\n"+
            "Location: /"+filename+"/\r\n\r\n");
          out.close();
          return;
        }

        System.out.println(filename);
        // filename = "action_page.php?firstname=Mickey"
        String value = "";
        String[] params = filename.split("\\?");
        if (params.length > 1) {
          String[] keyValues = params[1].split("=");
          if (keyValues.length > 1) {
            String key = keyValues[0];
            value = keyValues[1];
          }
        }
        System.out.println(value);
        if (value != "") {
          out.print(value);
          out.close();
        } else {

          // Open the file (may throw FileNotFoundException)
          InputStream f=new FileInputStream(filename);

          // Determine the MIME type and print HTTP header
          String mimeType="text/plain";
          if (filename.endsWith(".html") || filename.endsWith(".htm"))
            mimeType="text/html";
          else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg"))
            mimeType="image/jpeg";
          else if (filename.endsWith(".gif"))
            mimeType="image/gif";
          else if (filename.endsWith(".class"))
            mimeType="application/octet-stream";
          out.print("HTTP/1.0 200 OK\r\n"+
            "Content-type: "+mimeType+"\r\n\r\n");

          // Send file contents to client, then close the connection
          byte[] a=new byte[4096];
          int n;
          while ((n=f.read(a))>0)
            out.write(a, 0, n);
          out.close();
        }
      }
      catch (FileNotFoundException x) {
        out.println("HTTP/1.0 404 Not Found\r\n"+
          "Content-type: text/html\r\n\r\n"+
          "<html><head></head><body>"+filename+" not found</body></html>\n");
        out.close();
      }
    }
    catch (IOException x) {
      System.out.println(x);
    }
  }
}
