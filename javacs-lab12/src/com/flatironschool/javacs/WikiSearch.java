package com.flatironschool.javacs;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.StringBuilder;

import redis.clients.jedis.Jedis;


/**
 * Represents the results of a search query.
 *
 */
public class WikiSearch {
	
	// map from URLs that contain the term(s) to relevance score
	private Map<String, Integer> map;

	private static ServerSocket serverSocket;

	/**
	 * Constructor.
	 * 
	 * @param map
	 */
	public WikiSearch(Map<String, Integer> map) {
		this.map = map;
	}
	
	/**
	 * Looks up the relevance of a given URL.
	 * 
	 * @param url
	 * @return
	 */
	public Integer getRelevance(String url) {
		Integer relevance = map.get(url);
		return relevance==null ? 0: relevance;
	}
	
	/**
	 * Prints the contents in order of term frequency.
	 * 
	 * @param map
	 */
	private  void print() {
		List<Entry<String, Integer>> entries = sort();
		for (Entry<String, Integer> entry: entries) {
			System.out.println(entry);
		}
	}

	public String toStringQuery() {
		StringBuilder sb = new StringBuilder();
		List<Entry<String, Integer>> entries = sort();
		for(Entry<String, Integer> entry: entries) {
			sb.append("<a href =" + entry.getKey() + ">" + entry.getKey() + "</a>" + "</br>" + "<br>" + "<br>" + "<br>");
		}
		return sb.toString();
	}
	
	/**
	 * Computes the union of two search results.
	 * 
	 * @param that
	 * @return New WikiSearch object.
	 */
	public WikiSearch or(WikiSearch that) {
		Map<String, Integer> union = new HashMap<String, Integer>(map);
		for (String term: that.map.keySet()) {
			int relevance = totalRelevance(this.getRelevance(term), that.getRelevance(term));
			union.put(term, relevance);
		}
		return new WikiSearch(union);
	}
	
	/**
	 * Computes the intersection of two search results.
	 * 
	 * @param that
	 * @return New WikiSearch object.
	 */
	public WikiSearch and(WikiSearch that) {
		Map<String, Integer> intersection = new HashMap<String, Integer>();
		for (String term: map.keySet()) {
			if (that.map.containsKey(term)) {
				int relevance = totalRelevance(this.map.get(term), that.map.get(term));
				intersection.put(term, relevance);
			}
		}
		return new WikiSearch(intersection);
	}
	
	/**
	 * Computes the intersection of two search results.
	 * 
	 * @param that
	 * @return New WikiSearch object.
	 */
	public WikiSearch minus(WikiSearch that) {
		Map<String, Integer> difference = new HashMap<String, Integer>(map);
		for (String term: that.map.keySet()) {
			difference.remove(term);
		}
		return new WikiSearch(difference);
	}
	
	/**
	 * Computes the relevance of a search with multiple terms.
	 * 
	 * @param rel1: relevance score for the first search
	 * @param rel2: relevance score for the second search
	 * @return
	 */
	protected int totalRelevance(Integer rel1, Integer rel2) {
		// simple starting place: relevance is the sum of the term frequencies.
		return rel1 + rel2;
	}

	/**
	 * Sort the results by relevance.
	 * 
	 * @return List of entries with URL and relevance.
	 */
	public List<Entry<String, Integer>> sort() {
		// NOTE: this can be done more concisely in Java 8.  See
		// http://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values-java

		// make a list of entries
		List<Entry<String, Integer>> entries = 
				new LinkedList<Entry<String, Integer>>(map.entrySet());
		
		// make a Comparator object for sorting
		Comparator<Entry<String, Integer>> comparator = new Comparator<Entry<String, Integer>>() {
            @Override
            public int compare(Entry<String, Integer> e1, Entry<String, Integer> e2) {
                return e1.getValue().compareTo(e2.getValue());
            }
        };
        
        // sort and return the entries
		Collections.sort(entries, comparator);
		return entries;
	}


	/**
	 * Performs a search and makes a WikiSearch object.
	 * 
	 * @param term
	 * @param index
	 * @return
	 */
	public static WikiSearch search(String term, JedisIndex index) {
		Map<String, Integer> map = index.getCounts(term);
		return new WikiSearch(map);
	}

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

	// public static void main(String[] args) throws IOException {
		
	// 	// make a JedisIndex
	// 	Jedis jedis = JedisMaker.make();
	// 	JedisIndex index = new JedisIndex(jedis); 
		
	// 	// search for the first term
	// 	String term1 = "ibm";
	// 	System.out.println("Query: " + term1);
	// 	WikiSearch search1 = search(term1, index);
	// 	search1.print();
		
	// 	// search for the second term
	// 	String term2 = "programming";
	// 	System.out.println("Query: " + term2);
	// 	WikiSearch search2 = search(term2, index);
	// 	search2.print();
		
	// 	// compute the intersection of the searches
	// 	System.out.println("Query: " + term1 + " AND " + term2);
	// 	WikiSearch intersection = search1.and(search2);
	// 	intersection.print();
	// }
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
			out.print("HTTP/1.0 200 OK\r\n"+
            "Content-type: text/html\r\n\r\n");
			// make a JedisIndex
			Jedis jedis = JedisMaker.make();
			JedisIndex index = new JedisIndex(jedis); 
			
			out.println("<font face=\"verdana\" color=\"blue\"> Query: "  + value + "</font></br> <br> <br>");
			out.println("Results:")
			outl.println("</br> <br> <br>")
			WikiSearch search1 = WikiSearch.search(value.toLowerCase(), index);

          	out.print(search1.toStringQuery() );
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