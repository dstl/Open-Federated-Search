// Dstl (c) Crown Copyright 2018

package uk.gov.dstl.fedsearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Servlet implementation class Webapp
 */
@WebServlet("/FedSearch")
public class Webapp extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static GaianHandler gh= new GaianHandler();
	private static final String SYSTEM_OR_NETWORK=lookupConfigData("SYSTEM_NAME");
	private static final String ADMIN_EMAIL=lookupConfigData("ADMIN_EMAIL");
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Webapp() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action=request.getParameter("action");
	    PrintWriter writer = response.getWriter();
	    String checkboxes="";
	    String hiddenData="";
	    if(action==null){action="1";}
	    
	    switch (action){
	    case "1":
	    	response.setHeader("Content-Type", "text/html");
	    	try {
	    		GaianHandler gh=new GaianHandler();
				ResultSet rs=gh.gaianQuery("call DESCRIBE_REPO(' ')", "");
				Properties props=new Properties();
				
				while (rs.next()){
					String repo_name=rs.getString("REPO_NAME");
					if(!props.containsKey(repo_name)){
						Properties p2=new Properties();
						props.put(repo_name, p2);
					
					}
					String level1=rs.getString("LEVEL1");
					String level2=rs.getString("LEVEL2");
					Properties p3=(Properties) props.get(repo_name);
				    p3.setProperty(level1, level2);
				    props.put(repo_name, p3);
				}
				
				// PUT KEYS INTO A SET TO SORT THEM
				Enumeration<Object> keys=props.keys();
				SortedSet<String> keySet=new TreeSet<String>();
				while (keys.hasMoreElements()){
			    	Object key=keys.nextElement();
			    	keySet.add(key.toString());
				}	
				
				ArrayList<String> sortedList=new ArrayList<String>(keySet);
				
				checkboxes=("<div class=\"rTable\"><div class=\"rTableRow\"><div class=\"rTableHead1\">Tick to exclude whole repository</div>"+
              "<div class=\"rTableHead1\">Tick to exclude some indexes (expands)</div></div>");
				
				int counter=0;
				for (String key: sortedList){
					counter++;
					checkboxes+="<div id=\""+counter+"\" class=\"rTableRow\">";
					checkboxes+="<div class=\"rTableCell1\"><input type=\"checkbox\" name=\"norepo\" value=\""+key+"\" onchange=\"clickRepo("+counter+")\">"+key+"</div>";
					Properties p5=(Properties) props.get(key);
					if(p5.containsKey("not available")){
					    checkboxes+="<div class=\"rTableCell1\"><input id=\"A"+counter+"\" type=\"checkbox\" name=\"notused\" value=\"\" disabled>Not available</div>";
					} else {
						checkboxes+="<div class=\"rTableCell1\"><input id=\"A"+counter+"\" type=\"checkbox\" name=\"notused\" value=\"\" onchange=\"clickIndex('A1',"+counter+")\">"+key+"</div>";
					}
					checkboxes+="</div>";
							    	
				}
				checkboxes+="</div><div>";
				for (String key: sortedList){
					Properties p4=(Properties) props.get(key);
			    	Set<Object> indexes1= p4.keySet();
			    	SortedSet<String> sortedSet=new TreeSet<String>();
			    	for (Object o: indexes1){
			    		String k=o.toString();
			    		sortedSet.add(k);
			    		
			    	}	
					String repoIndexList="";
				
			    	for (String index:sortedSet){
			    		String types=(String) p4.get(index);
			    		repoIndexList+=key+"/"+index+"/"+types+";";
			    	}
			    	repoIndexList=repoIndexList.substring(0,repoIndexList.length()-1);
		    		hiddenData+="<input type=\"hidden\" name=\"indexes\" value=\""+repoIndexList+"\" disabled>";
				}
		    	hiddenData+="</div>";

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
	    	
	    	String script=loadStaticWebContent("script1.htm");
	    	String page1=loadStaticWebContent("Page1.htm");
	    	writer.write(script);
	    	writer.write(page1);
	    	writer.write("<BR><BR>");
	    	writer.write(checkboxes+hiddenData);
	    	writer.write("</FORM></BODY></HTML>");
	    	writer.flush();
	    	break;
	    case "2": // make the source data easier to read by indenting JSON fields
	    	response.setHeader("Content-Type", "application/json");
	    	String URL=request.getParameter("url");
	    	if(URL.length()>0){
	    		HTTP http=new HTTP();
	    		try {
					String json=http.getHTTP(URL);
					ObjectMapper mapper = new ObjectMapper();
					Object js = mapper.readValue(json, Object.class);
					String prettyJson=mapper.writerWithDefaultPrettyPrinter().writeValueAsString(js);
					prettyJson=prettyJson.replace("\\\"", "\"");
					prettyJson=prettyJson.replace("\\n", "<BR>");
					prettyJson=prettyJson.replace("\n", "<BR>");
					prettyJson=prettyJson.replace("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
					prettyJson=prettyJson.replace("],", "],<BR>");
					writer.write(prettyJson);
					writer.flush();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	    		
	    	} else {
	    		writer.write("missing url parameter in request");
		    	writer.flush();
	    	}
	    	
	    	break;
	    case "3": // load the help page
	    	response.setHeader("Content-Type", "text/html");
	    	String help=loadStaticWebContent("Help.htm");
	    	writer.write(help);
	    	writer.flush();
	    	break;
	    case "4": // do a query using the URL parameters
	    	response.setHeader("Content-Type", "text/html");
	    	doQuery(request,response);
	    	break;
	    case "opensearch": // provide OpenSearch description file (xml)
	    	response.setHeader("Content-Type", "text/xml");
	    	String openSearch=loadStaticWebContent("OFS_Opensearch_Description_template.xml");
	    	String thisURL=request.getRequestURL().toString();
	    	openSearch=openSearch.replace("{OFS:protocol_port_hostname_path}",thisURL);
	    	openSearch=openSearch.replace("{OFS:system_or_network}", SYSTEM_OR_NETWORK);
	    	openSearch=openSearch.replace("{OFS:adminEmailAddress}", ADMIN_EMAIL);
	    	writer.write(openSearch);
	    	writer.flush();
	    	break;
	    default:
	    	writer.write("Error 404 - action not found - try action=1" );
	    	writer.flush();
	    }
	    
	}
	


private void doQuery(HttpServletRequest request,HttpServletResponse response) throws IOException{
	
	int size=10;  // this is the number of hits per page -- PER REPO ..
    // i.e. this parameter is passed-on to each search engine  (as &size=..)
	// The actual number of hits the federated search user sees will depend on how many search engines respond
	// This parameter could be exposed for the user to select - but is not at present.
	
	Set<Hit> results=new TreeSet<Hit>();
	int currentPage=1; // default - form response may contain a different number
	
	PrintWriter writer = response.getWriter();
	 response.setHeader("Content-Type", "text/html");
	 
	 
	 String head="<html><head><style>"+
	 "em   {color: red; font-weight: bold;}"+
	 "</style></head><body>";
	 
	 
	writer.write(head);
	
   String prevParams=request.getQueryString();
   System.out.println("request="+prevParams);
   System.out.println("timeWindow="+request.getParameter("timeWindow"));
   
   String pw=request.getParameter("pw");
   if (pw!=null){currentPage=Integer.parseInt(pw);};
   String query=request.getParameter("q");  
   String exclude=request.getParameter("exclude");
  
	exclude=URLDecoder.decode(exclude,"UTF-8");
	exclude=exclude.replace("'","\"");
	if(exclude.length()>0){writer.write("Excluding: "+exclude);}
	if(exclude.trim().length()>0){
		query+="&exclude="+exclude;
	}
	
	
	String dtstart="";
	String timeWindow=request.getParameter("timeWindow");
	if(timeWindow!=null){
		String offset="";
		String now = new SimpleDateFormat("yyyy-MM-dd").format(new Date())+"||";
					
		System.out.println(now);
		switch (timeWindow){
		case "anytime":
			offset="";
			break;
		case "lastDay":
			offset="-1d/h";
			break;
		case "last2Days":
			offset="-2d/h";
			break;
		case "last3Days":
			offset="-3d/h";
			break;
		case "lastWeek":
			offset="-1w/h";
			break;
		case "last2Weeks":
			offset="-2w/h";
			break;
		case "lastMonth":
			offset="-1M/h";
			break;
		case "last3Months":
			offset="-3M/h";
			break;
		case "last6Months":
			offset="-6M/h";
			break;
		case "lastYear":
			offset="-1y/h";
				
		}
        if (offset.length()>0){
        	dtstart="&dtstart="+now+offset;
        }
	}
	
	
   // This OFS (output) format for excluding indexes is &noindex=repoName1/index1/index2,repoName2/index3/...
   // Exclude whole repositories as follows: &norepo=repoName1, repoName2
  
   
   String excludeIndexes="&noindex=";
   String excludeRepos="&norepo=";
   Properties repoToIndexList=new Properties();
   String moreParams=request.getQueryString();
   if(moreParams.length()>0){
       String[] params=moreParams.split("&");
       for (int i=0;i<params.length;i++){
    	  String param=params[i];
    	  if(param.startsWith("norepo=")){
    		  param=param.substring(7);
    		  excludeRepos+=param+",";
    	  } else if(param.startsWith("exindex_")){
    			  param=param.substring(8);
    			  String[] indexes=param.split("=");
    			  String repo=indexes[0];
    			  ArrayList<String> indexList=new ArrayList<String>();
    			  if(repoToIndexList.containsKey(repo)){
    				  indexList=(ArrayList<String>) repoToIndexList.get(repo);
    			  } 
    			  indexList.add(indexes[1]);	  
    			  repoToIndexList.put(repo, indexList);
          }
       }
       if(excludeRepos.endsWith(",")){
    	 excludeRepos=excludeRepos.substring(0,excludeRepos.length()-1);   
       }
       boolean isFirst=true;
       Set<Object> ips= repoToIndexList.keySet();
       for (Object ipo:ips){
    		  String ip=ipo.toString();
    		  ArrayList<String> indexList=(ArrayList<String>) repoToIndexList.get(ip);
    		  if(isFirst){
    			isFirst=false;  
    		  } else {
    			excludeIndexes+=",";
    		  }
    		  excludeIndexes+=ip;
    		  for (String index:indexList){
    			  excludeIndexes+="/"+index;
    		  }
       }
       query+=dtstart+excludeRepos+excludeIndexes+"&size="+size+"&pw="+currentPage;
   }
   String query2="q="+URLDecoder.decode(query,"UTF-8");
	query2=query2.replace("\"", "\\\"");
  System.out.println("query2="+query2);

   
   try {
	ResultSet rs=gh.gaianQuery("call OPENSEARCH('"+query2+"')", "");
	
	boolean gotResults=false;
	while(rs.next()){
		String snips=rs.getString("SNIPS")+" ";
		String title=rs.getString("TITLE"); 
		String source_URL=rs.getString("SOURCE_URL"); // the URL of the original document on a web-server
		Hit hit=new Hit(title, rs.getString("URL"),source_URL,snips,rs.getInt("RATING"),rs.getInt("HIT_COUNT"),rs.getString("FROM_REPO"),rs.getString("DATE_STRING"));
		results.add(hit);
		gotResults=true;
		
	}
	rs.close();
	if(!gotResults){
		writer.write("No results found for this search.</body></HTML>");
	       writer.flush();
	       return;
	}
	
	
	                              
	int largestTotalHits=0; 
    	
	for (Hit h:results){
		
		if(!h.title.contains("error code")&& !h.snips.startsWith("java.")){

			if(h.total_hits>largestTotalHits){
				largestTotalHits=h.total_hits;
			}
	
			String repoNameIndexType=h.repoName;
			String dc_valid=h.date_string;
			h.snips=h.snips.substring(4); // remove first <BR>
	
			if(h.source_URL==null||h.source_URL.length()<1){  // no original doc URL so return Elasticsearch record
			    writer.write("<BR><div><A href=\"/OFS/FedSearch?action=2&url="+h.URL+"?pretty=true\">"+h.title+"</A><BR>"+h.snips+"<BR><small>"+repoNameIndexType+"&nbsp;&nbsp"+dc_valid+"</small></div>");
			} else {
				// hyperlink to source_URL
				writer.write("<BR><div><A href=\""+h.source_URL+"\">"+h.title+"</A><BR>"+h.snips+"<BR><small>"+repoNameIndexType+"&nbsp;&nbsp"+dc_valid+"</small></div>");	
			}
		} else {
			writer.write(h.title+"<BR>"+h.snips);
			writer.flush();
			return;
		}
	 	
	}
	 
	 if(prevParams.contains("&action=")){
		 int posAction=prevParams.indexOf("&action=");
		 prevParams=prevParams.substring(0,posAction+8)+"4"+prevParams.substring(posAction+9);
	 } else {
	     prevParams+="&action=4";
	 }
	 if(prevParams.contains("&pw=")){
		 prevParams=prevParams.replaceAll("&pw=[0-9]*","");
	 }
	 writer.write("<BR><BR>");
	 int nextPage=currentPage+1;
	 int noPages=(int) largestTotalHits/size+1;
	 writer.write("<a href=\"http://localhost:8080/OFS/FedSearch?"+prevParams+"&pw=1\"><em>First page</em></a>&nbsp;&nbsp;");
	 writer.write("&nbsp;&nbsp;Page "+currentPage+" of "+noPages+" &nbsp;&nbsp;");
	 writer.write("<a href=\"http://localhost:8080/OFS/FedSearch?"+prevParams+"&pw="+nextPage+"\"><em>Next page</em></a>");
	 writer.write("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"http://localhost:8080/OFS/FedSearch?action=1\"><em>New Search</em></a>");
	
	   writer.write("</body></HTML>");
       writer.flush();
} catch (Exception e) {
	// TODO Auto-generated catch block
	String msg=e.getMessage();
	if (msg==null){
		msg=e.getClass().getName();
	} 
	writer.write(msg);
	StringWriter sw = new StringWriter();
	e.printStackTrace(new PrintWriter(sw));
	String exceptionAsString = sw.toString();
	writer.write(exceptionAsString);
	writer.write("</body></HTML>");
       writer.flush();
}

	
}

	
	/** Retrieves a file from the servlet's /WEB-INF/ folder and converts it to a string.
 * @param filename  - the file in the  /WEB-INF/ folder to be retrieved
 * @return the string content of the retrieved file
 */
private  String loadStaticWebContent(String filename){
	InputStream inputStream = getServletContext().getResourceAsStream("/WEB-INF/"+filename);
	ByteArrayOutputStream result = new ByteArrayOutputStream();
	byte[] buffer = new byte[1024];
	int length;
	try {
		while ((length = inputStream.read(buffer)) != -1) {
		    result.write(buffer, 0, length);
		}
		return result.toString("UTF-8");	
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return "error: problem with file="+filename;
}

private static String lookupConfigData(String colName){
	String myIP=getIPv4Address();
	String data="CONFIG ERROR";
	String SQL="SELECT "+colName+" FROM DB_OFS_CONFIG WHERE IPV4='"+myIP+"'";
	
	try {
	  ResultSet rs0=gh.gaianQuery(SQL,"");
			if(rs0.next()){
			  data=rs0.getString(colName);
			}
	  } catch (SQLException e) {
			// TODO Auto-generated catch block
		e.printStackTrace();
	  } catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	return data;
}

public static String getIPv4Address(){
	 String IPv4="";
	 boolean found=false;
	 try {
			Enumeration<NetworkInterface> netInterfaces=NetworkInterface.getNetworkInterfaces();
		    while(!found && netInterfaces.hasMoreElements()){
		        NetworkInterface ni=(NetworkInterface)netInterfaces.nextElement(); 
		       // System.out.println(ni.getName());
		        Enumeration<InetAddress> ips=ni.getInetAddresses();
		        while (!found && ips.hasMoreElements()){
		        	InetAddress ip=ips.nextElement();
		        	IPv4=ip.toString();
		        	IPv4=IPv4.substring(1); // remove leading slash
		        	if (!IPv4.startsWith("127.") && !IPv4.contains(":")&&!ni.getName().startsWith("docker")){ // discount localhost and IPv6 addresses
		        		found=true;
		        	}	
		        }
		    }
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 return IPv4;
}

}