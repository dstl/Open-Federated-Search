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
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;




/**
 * Servlet implementation class REST
 */
@WebServlet("/REST")
public class REST extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static GaianHandler gh= new GaianHandler();
	private static final String SYSTEM_NAME=lookupConfigData("SYSTEM_NAME"); // This will appear in RSS "<Title>" as  "Open Federated Search on {SYSTEM_NAME} : {searchTerms}"                                                                     // Should be tailored. e.g. to add "provided by ..." or "of {some-org's repositories}"
 //   
    /**
     * @see HttpServlet#HttpServlet()
     */
    public REST() {
        super();
    }

	/**  
	 *  This REST interface allow multiple repositories to be searched at once, each with their own search engines.
	 *  The GaianDB is used to distribute the search expression in an extended OpenSearch format.
	 *  Each GaianDB node receiving the distributed search expression converts it into the query language of the
	 *  search engine that node is set-up to serve. 
	 *  
	 *  Currently only Elasticsearch search engines are supported, but it would be easy to support others as well.
	 *  
	 *  Returns a set of "hits" which each provide meta-data about, and extracts from, the matching record. 
	 *  Each result comprises:
	 *   - the title and URL of the matching record.
	 *   - snippets of text containing the matching search terms
	 *   - the name assigned to the respective repository being searched
	 *   - the index and type used by the respective search engine to provide this result
	 *   - (if available) a date associated with the matching record
	 *      
	 *  Note the purpose of providing a repository name and index is to allow iterative exclusion of search engines and indexes
	 *  (Note the index and type are returned together slash-separated - index/type - but currently 
	 *   there is no capability to selectively exclude "types" just whole indexes. 
	 *   The type is provided for possible future use and because it provides hints to the users about the content of the indexes.
	 *  
	 *  The responses may be provide in RSS (per OpenSearch spec) and JSON or HTML (JSON default).
	 *  
	 *  The OpenSearch description document provides the API see: 
	 *     /OFS/WebContent/WEB-INF/OFS_Opensearch_Description_template.xml
	 *  
	 *   JSON result format is:
 		 { "numberOfResults": 5,
 		   "results": [ {
 		     "dc:title":"the title",
 		     "OFS:link":"http://hostname:9200/email/message/AV4-ZZzVvj2vuDMeamGL", --- URL of full record
 		     "OFS:matchingText":"some text",  --- snippet(s) containing matched search terms
 		     "OFS:repoName":"", --- the name of the repository which was searched to provided this result  
 		     "OFS:repoIndex":"email/message", --- search engine index (email) and type (message) (from OFS:link field)
 		     "dc:date":2017-10-23"},    --- value can be a date, a date-time or "unknown".
 		   }, {...}]
 		 }  
        
	 *  
	 *  
	 *  The query is OpenSearch format plus some additional optional parameters.
        
        example query:   http://127.0.0.1:8080/OFS/FedOpenSearch?q=attack
        
        Only the "q" parameter is required.
        
 		The full set of possible parameters is:
        q={searchTerms} where {searchTerms} is a URL encoded list of space separated terms
                         use double quotes (%22) around exact phrase matches 
                         
        pw={startPage}  where startPage is an integer  (starting at 1 for first page)
        
        size={count}    where count is an integer number of hits per page (per search engine) 
                        
        exclude={exludedTerms} where {excludedTerms} is a URL encoded list of space separated terms
                         use double quotes (%22) around exact phrase matches.
                         NB the exclusion of terms is applied to the snippets of matching text, not the whole document, 
                         and so must appear in the same field as the matching text, normally in the same sentence.
    	
         bbox={geo:box}  where {geo:box} is a comma separated list of numbers: minLon, minLat, maxLon, maxLat forming a bounding box
            
         dtstart={xsd:dateTime} applies a time filter to the field "dcterms:valid" in form 2017-10-23 or 2017-10-23T12:02:34 
         dtend={xsd:dateTime} applies a time filter to the field "dcterms:valid" in form 2017-10-23 or 2017-10-23T12:02:34
                         NB Do not use these if the data-set does not have a "dcterms:valid" field.                  
	     	
         norepo={repoList} Exclude searching of named repositories, where {repoList} is a comma separated list of repo names.
         
         noindex={indexList} Exclude searching of specific indexes on specific repository name, where
         {indexList} is slash separated list of indexes to ignore on the specified repository name; 
         comma separated between repo names, e.g. &noindex=repo1/index1/index2,repo2/index3/index4 
         format=json or html
 		   

	**/ 
        	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String query=request.getQueryString();
		query=URLDecoder.decode(query);
		String format=request.getParameter("format");
		String channel="";
		String items="";
		String itemTemplate="";
		if (format==null){format="json";};
		PrintWriter writer = response.getWriter();
		Set<Hit> results=new TreeSet<Hit>();
		if (format.equals("json")){ response.setHeader("Content-Type", "application/json");}
		if (format.equals("html")){ 
			response.setHeader("Content-Type", "text/html");
			String head="<html><head><style>"+
					 "em   {color: red; font-weight: bold;}"+
					 "</style></head><body>";		 
					writer.write(head);
			}

		String query2=query.replace("\"", "\\\"");
      System.out.println("query2="+query2);

       
       try {
		ResultSet rs=gh.gaianQuery("call OPENSEARCH('"+query2+"')", "");
		/*
		ResultSetMetaData rsmd=rs.getMetaData();
		for (int i=0;i<rsmd.getColumnCount();i++){
			System.out.println(rsmd.getColumnName(i+1));
		}
        */	
		String lastRepo="";
		int totalHits=0;
		boolean gotResults=false;
		while(rs.next()){
			String snips=rs.getString("SNIPS")+" ";
			String title=rs.getString("TITLE"); 
			String source_URL=rs.getString("SOURCE_URL");
			String from_repoIndexType=rs.getString("FROM_REPO"); // format  repoName/index/type
			int hit_count=rs.getInt("HIT_COUNT");
			Hit hit=new Hit(title, rs.getString("URL"),source_URL, snips,rs.getInt("RATING"),hit_count,from_repoIndexType,rs.getString("DATE_STRING"));
			results.add(hit);
			gotResults=true;
			// ADD UP THE HIT COUNT FROM EACH DISTINCT REPO
			String [] from_repo=from_repoIndexType.split("/");
			if(!from_repo[0].equals(lastRepo)){
				totalHits+=hit_count;
			}
			lastRepo=from_repo[0];
		}
		rs.close();
		if(!gotResults){
			if(format.equals("html")){
			writer.write("No results found for this search.</body></HTML>");
			} else {
				writer.write("{\"numberOfResults\":0,\"results\":[]}");  // as returned here, total number of hits may be higher if paged
			}
		    writer.flush();
		    return;
		} else {
			switch (format){
			case "json":
				writer.write("{\"numberOfResults\":"+totalHits+",\"results\":[");
				break;
			case "html":
				writer.write("Total "+totalHits+" results");
				break;
			case "rss":
				channel=loadStaticWebContent("RSS_Channel_Template.xml");
				channel=channel.replace("{OFS:system_or_network}", SYSTEM_NAME);
				String scheme = request.getScheme();             
				String serverName = request.getServerName();     
				int serverPort = request.getServerPort(); 
				String OFS_Opensearch_Description_xml_URL=scheme+"://"+serverName+":"+serverPort+"/OFS/FedSearch?action=opensearch";
				channel=channel.replace("{OFS:opensearchdescription_URL}", OFS_Opensearch_Description_xml_URL);
				String searchTerms=request.getParameter("q");
				String queryString=request.getQueryString();
				queryString=URLEncoder.encode(queryString);
				String requestURL=request.getRequestURL().toString()+"?"+queryString;
				String pw=request.getParameter("pw");
				if(pw==null){pw="1";};
				channel=channel.replaceAll("\\{opensearch:searchTerms\\}", searchTerms);
				channel=channel.replace("{OFS:requestURL}", requestURL);
				channel=channel.replace("{opensearch:totalResults}", String.valueOf(totalHits));
				channel=channel.replace("{opensearch:startIndex}",pw ); // note this is a page index
				itemTemplate=loadStaticWebContent("RSS_item_template.xml");
				break;
			default:
				writer.write("The format "+format+" is not supported.");
				
			}
			
		}
		
		String repoIndex="";
		String repoName="";
	    
		// here "repoIndex" is the index/type in Elasticsearch (or the equivalent in other search engines)
		boolean first=true;
		for (Hit h:results){
			
					
			if(!h.title.contains("error code")&& !h.snips.startsWith("java.")){

				String date_string=h.date_string;
				String from_repo=h.repoName; // normally contains repoName/index/type 
				int posSlash=from_repo.indexOf("/");
				if(posSlash>0){
					repoName=from_repo.substring(0,posSlash);
					repoIndex=from_repo.substring(posSlash+1);
				} else {
					repoName=from_repo;
					repoIndex="";
				}
				
				
				h.snips=h.snips.substring(4); // remove first <BR>
				switch (format){
				
				
				case "html":
					if(h.source_URL==null||h.source_URL.length()<1){  // no original doc URL so return Elasticsearch record	
				//	writer.write("<BR><A href=\""+h.URL+"?pretty=true\">"+h.title+"</A><BR>"+h.snips+"<BR><small>"+repoName+"  score="+h.score+"</small><BR>");			h.repoName=repoName;
					writer.write("<BR><div\"><A href=\"/OFS/FedSearch?page=2&url="+h.URL+"?pretty=true\">"+h.title+"</A><BR>"+h.snips+"<BR><small>"+from_repo+"&nbsp;&nbsp"+date_string+"</small></div><BR>");
					
					} else {
						// hyperlink to source_URL
						writer.write("<BR><div><A href=\""+h.source_URL+"\">"+h.title+"</A><BR>"+h.snips+"<BR><small>"+from_repo+""+"&nbsp;&nbsp"+date_string+"</small></div><BR>");
					}
				    break;
				
				case "json":
					if(!first){writer.write(",");}
					if (first){first=false;}
					if(h.source_URL==null||h.source_URL.length()<1){  // no original doc URL so return Elasticsearch record
				    	writer.write("{\"dc:title\":\""+h.title+"\",\"OFS:link\":\""+h.URL+"\",");
					} else {
						writer.write("{\"dc:title\":\""+h.title+"\",\"OFS:link\":\""+h.source_URL+"\",");
					}
					String matchingText=h.snips;
					matchingText=matchingText.replace("<em>","");
					matchingText=matchingText.replace("</em>","");
					writer.write("\"OFS:matchingText\":\""+matchingText+"\",");
					writer.write("\"OFS:repoName\":\""+repoName+"\",");
					writer.write("\"OFS:repoIndex\":\""+repoIndex+"\",");
					if (date_string.length()<3){date_string="unknown";}
					writer.write("\"dc:date\":\""+date_string+"\"}");
					break;
				case "rss":
					String item=itemTemplate;
					item=item.replace("{dc:title}", h.title);
					item=item.replace("{OFS:link}",h.URL);
					String matchingRSStext=h.snips;
					matchingRSStext=matchingRSStext.replaceAll("<BR>", "");
					matchingRSStext=matchingRSStext.replaceAll("&nbsp;", "");
					matchingRSStext = matchingRSStext.replaceAll("\\p{C}", "");// removes non-printable characters from Unicode
					item=item.replace("{OFS:matchingText}",matchingRSStext);
					item=item.replace("{dc:date}",h.date_string);
					item=item.replace("{OFS:repoName}",repoName);
					item=item.replace("{OFS:repoIndex}",repoIndex);
					items+=item;
				}
			} else { // error case
				writer.write(h.title+"<BR>"+h.snips);
				writer.flush();
				return;
			}
		 	
		} // next hit
		   // ALL DONE - NOW FINISH OFF
		   switch (format){
		   case "html":
		      writer.write("</body></HTML>");
		      break;
		   case "json":
			   writer.write("]}");
			   break;
		   case "rss":
			   channel=channel.replace("{OFS:items}",items);
			   writer.write(channel);
		   }
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
