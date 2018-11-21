// Dstl (c) Crown Copyright 2018
package uk.gov.dstl.fedsearch;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



@WebServlet("/Describe")
public class Describe extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private GaianHandler gh= new GaianHandler();
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Describe() {
        super();
    }

	/**
	 * Gets a list of all the search engines in the federation (by IP address) and lists the indexes and types defined
	 * on each one. No parameters.   
	 * 
	 * invoke as:    http://correctIPaddress:8080/OFS/Describe
	 *
	 * example results:
	 * {"results":["searchEngineIPAddress":"192.168.1.55",
	 *      "indexes":[".kibana/dashboard,index-pattern,search,visualization,config",
	 *                 "logstash* /logs, default_",
	 *  		       "repo1/pdf",
	 *                 "repo3/xml",
	 *                 "twitter/tweet,user"
	 *                  ]
	 *	              ]}
	 *
	 *The "indexes" field comprises:
	 * - the name of the index
	 * - a forward slash
	 * - a comma separated list of the types defined for this index.
	 *
	 *Note that logstash indexes are summarised (they have a suffix of the date, one per day).
	 */
	 
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		PrintWriter writer = response.getWriter();
		response.setHeader("Content-Type", "application/json");
	
	try {
	//	GaianHandler gh=new GaianHandler(); ### PREVIOUSLY THIS WAS ACTIVE - CHECK IT STILL WORKS WITH CLASS VARIABLE gh
	//  ## Is this a scalability issue? How best to handle multiple simultaneous searches?	
	//  ## check all resultSets are closed when no longer needed.	
		ResultSet rs=gh.gaianQuery("call DESCRIBE_REPO(' ')", "");
		Properties props=new Properties();
		
		while (rs.next()){
			String IPv4=rs.getString("IP");
			if(!props.containsKey(IPv4)){
				Properties p2=new Properties();
				props.put(IPv4, p2);
			
			}
			String level1=rs.getString("LEVEL1");
			String level2=rs.getString("LEVEL2");
			Properties p3=(Properties) props.get(IPv4);
		    p3.setProperty(level1, level2);
		    props.put(IPv4, p3);
		}
		rs.close();
		
		// PUT KEYS INTO A SET TO SORT THEM
		Enumeration<Object> keys=props.keys();
		SortedSet<String> keySet=new TreeSet<String>();
		while (keys.hasMoreElements()){
	    	Object key=keys.nextElement();
	    	keySet.add(key.toString());
		}	
		
		ArrayList<String> sortedList=new ArrayList<String>(keySet);
		
		boolean firstIP=true;
		for (String key: sortedList){
		    if (firstIP){
		    	writer.write("{\"results\":[");
		    	firstIP=false;
		    } else {
		    	writer.write(",");
		    }
			writer.write("\"searchEngineIPAddress\":\""+key.toString()+"\",");
	    	Properties p4=(Properties) props.get(key);
	    //	writer.write("<BR>Repo="+key.toString()+"<BR>");
	    	Set<Object> indexes1= p4.keySet();
	    	SortedSet<String> sortedSet=new TreeSet<String>();
	    	for (Object o: indexes1){
	    		String k=o.toString();
	    		sortedSet.add(k);
	    		
	    	}
	    	boolean first=true;
	    	for (String index:sortedSet){
	    	//	writer.write("<BR>"+index.toString()+"<BR>");
	    		String types=(String) p4.get(index);
	    		if(!first){
	    			writer.write(",");
	    		} else {
	    			writer.write("\"indexes\":[");
	    			first=false;
	    		}
	    		writer.write("\""+index+"/"+types.toString()+"\"");
	    		
	    	}
	    	writer.write("]");
		}
		writer.write("]}");
		writer.flush();
 
	    
	    
	} catch (Exception e) {
		String msg=e.getMessage();
		if (e.getMessage()==null){
			msg = e.getClass().getName();
		}
		
		if (msg.contains("connection refused")){
			writer.write("Failed to connect to Elasticsearch");
		} else if (msg.contains("NullPointerException")){
			writer.write("ERROR: FAILED TO CONNECT TO LOCAL GAIANDB NODE - IS IT RUNNING?");
		} else {
			writer.write(msg);
		}
	}	

}
}	
