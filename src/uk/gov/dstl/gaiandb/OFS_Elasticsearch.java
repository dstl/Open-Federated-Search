// Dstl (c) Crown Copyright 2018
package uk.gov.dstl.gaiandb;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * This package is for use with the Dstl Open Federated Search tool (OFS - on GitHub)
 * and with the GaianDB (https://github.com/gaiandb/gaiandb).
 * 
 * It enables an instance of an Elasticsearch search engine to become part of the Open Federated Search system.  
 *
 * 
 * This package contains two stored procedures which need to be installed in the GaianDB.
 * Put this jar in the folder ../lib  on the gaiandb installation path (where derbyClient.jar already is).
 * Then connect to the GaianDB using a tool such as DBeaver, or the derby ij tool: 
 * 
 * 
 *   connect 'jdbc:derby://localhost:6414/gaiandb;user=gaiandb;password=passw0rd'; (note the zero in the password)    
 *   Then execute the SQL commands:
 *   
 *   CREATE PROCEDURE DESCRIBE_REPO(COL1 VARCHAR(10000)) PARAMETER STYLE JAVA MODIFIES SQL DATA LANGUAGE JAVA DYNAMIC RESULT SETS 1 EXTERNAL NAME 'uk.gov.dstl.gaiandb.OFS_Elasticsearch.describeRepo';	
 *   CREATE PROCEDURE OPENSEARCH(COL1 VARCHAR(10000)) PARAMETER STYLE JAVA MODIFIES SQL DATA LANGUAGE JAVA DYNAMIC RESULT SETS 1 EXTERNAL NAME 'uk.gov.dstl.gaiandb.OFS_Elasticsearch.OpenSearch';	
 */

public class OFS_Elasticsearch {
	
	// THE VALUES FOR THESE VARIABLES ARE LOADED FROM A GAIANDB VIRTUAL TABLE CALLED "FEDSEARCH_CONFIG".
	// THE CORRECT ROW OF THE TABLE IS DETERMINED FROM THIS NODE'S IP ADDRESS WHICH IS AUTOMATICALLY DETERMINED.
	// THE VALUES BELOW ARE DEFAULT VALUES (TO BE OVER-RIDDEN BY THE VALUES IN THE GAIAN TABLE).
	private static final String URL_FIELD="URL"; // MANDATORY TO PROVIDE A VALUE
	private static final String DATE_FIELD="dcterms:valid";//REQUIRED FOR SEARCHES ON DATE RANGES
    private static final String TITLE_FIELD="dc:title"; // HIGHLY DESIRABLE - IF ABSENT URL WILL BE USED AS TITLE
    private static final String GEO_FIELD="geo_shape"; // REQUIRED FOR SEARCHES WITHIN A BOUNDING BOX
     // |In the repository being searched this field must contain a point, linestring, polygon or other shape associated with the record as meta-data

	
public static void describeRepo(String query,ResultSet[] rs){
	// CREATE PROCEDURE DESCRIBE_REPO(COL1 VARCHAR(10000)) PARAMETER STYLE JAVA MODIFIES SQL DATA LANGUAGE JAVA DYNAMIC RESULT SETS 1 EXTERNAL NAME 'uk.gov.dstl.gaiandb.OFS_Elasticsearch.describeRepo';					
	String repo_name="unknown";
	
		 try {
			 
			 
			Class.forName("org.apache.derby.jdbc.ClientDriver"); 
			//Connection conn=DriverManager.getConnection("jdbc:derby://localhost:6414/gaiandb","gaiandb","passw0rd");
			Connection conn=DriverManager.getConnection("jdbc:default:connection");
			Statement s=conn.createStatement();
			String IPv4=getIPv4Address();
			
			String host="http://127.0.0.1:9200"; // used for testing when own IP address is dynamic and not in database
			String SQL="SELECT REPO_NAME,SEARCHENGINE_URL FROM FEDSEARCH_CONFIG WHERE GAIAN_NODE_IPV4='"+IPv4+"'";
			
			System.out.println(SQL);
			ResultSet rsc=s.executeQuery(SQL);
			if(rsc.next()){
				host=rsc.getString("SEARCHENGINE_URL");
				repo_name=rsc.getString("REPO_NAME");
				System.out.println("found this node's ipv4 address in GAIAN VIRTUAL TABLE FEDSEARCH_CONFIG");
			} else {
				System.out.println("WARNING: did not find this node's ipv4 address in GAIAN VIRTUAL TABLE FEDSEARCH_CONFIG");
				System.out.println("WARNING: using default search engine url");
			}
			System.out.println("SEARCH_ENGINE_URL="+host+"  (note urls need to have a http:// prefix)");
			System.out.println("REPO_NAME="+repo_name);
			host+="/";
			
			
			SQL="delete from DB_REPO_TEMP_TABLE where REPO_NAME<>'XXX'";
			s.execute(SQL);
			HTTP http=new HTTP();
			String es_response=http.getHTTP(host+"/_all/_mapping");
			System.out.println(es_response);
			System.out.println("**************");
			
			
			/*
			THIS IS WHAT YOU SHOULD DO IF YOUR SEARCH ENGINE CANNOT PROVIDE INFO ON ITS INDEXES/TYPES
	        (FOR ELASTICSEARCH LEVEL1=index, LEVEL2=type)
			Note the words "not available" must be inserted exactly as shown here. 
			SQL="insert into DB_REPO_TEMP_TABLE(REPO_NAME,LEVEL1,LEVEL2) VALUES ('"+repo_name+"','not available','not available')";
		 	s.execute(SQL);
		 	rs[0]=s.executeQuery("SELECT * FROM DB_REPO_TEMP_TABLE ORDER BY LEVEL1 ASC");
		 	return;
			
			*/
			
			
			
			
			ObjectMapper objectMapper = new ObjectMapper();

			try {

				JsonNode node = objectMapper.readValue(es_response, JsonNode.class);
				Iterator<String> fields=node.fieldNames();
				String typeList="";
				boolean doneLogstash=false;
				while(fields.hasNext()){
					typeList="";
					String field=fields.next();
					boolean doIt=true;
					
					// SPECIAL CASE: ONLY PROCESS FIRST INSTANCE OF INDEXES STARTING WITH LOGSTASH
					// AND REPLACE INDEX-NAME WITH logstash*
					// THIS TOOL IS NOT DESIGNED FOR SEARCHING LOGSTASH
					
					if(field.startsWith("logstash") && doneLogstash){
						doIt=false;
					}
					if(doIt){
						//	System.out.println(field);
						JsonNode n=node.get(field).get("mappings");						
						Iterator<String> types=n.fieldNames();
						while(types.hasNext()){
							String type=types.next();
						//	System.out.println("   "+type);
							typeList+=type+",";
						}
						typeList=typeList.substring(0,typeList.length()-1);
						if (field.startsWith("logstash")){
							field="logstash*";
							doneLogstash=true;
						}
						SQL="insert into DB_REPO_TEMP_TABLE(REPO_NAME,LEVEL1,LEVEL2) VALUES ('"+repo_name+"','"+field+"','"+typeList+"')";
						//	System.out.println(SQL);
					 	s.execute(SQL);
					}						
				}
				rs[0]=s.executeQuery("SELECT * FROM DB_REPO_TEMP_TABLE ORDER BY LEVEL1 ASC");
			} catch (Exception e) {
				System.out.println("catch 1   "+ e.getMessage());
				e.printStackTrace();
			
			}
			
		 } catch (Exception e) {
			    System.out.println("catch 2   "+e.getMessage());
				// TODO Auto-generated catch block
				//e.printStackTrace();
			    String msg=e.getMessage();
			    if (msg.contains("Connection refused")){msg="ERROR: Unable to connect to Elasticsearch from "+getIPv4Address();}
			    try {
					Class.forName("org.apache.derby.jdbc.ClientDriver");
					Connection conn=DriverManager.getConnection("jdbc:default:connection");
					Statement s=conn.createStatement();
					String SQL="insert into DB_REPO_TEMP_TABLE(REPO_NAME,LEVEL1,LEVEL2) VALUES ('"+repo_name+" - "+msg+"','"+msg+"','"+""+"')";
					s.execute(SQL);
					rs[0]=s.executeQuery("SELECT * FROM DB_REPO_TEMP_TABLE ORDER BY LEVEL1 ASC");
				} catch (ClassNotFoundException | SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} 
			
			
			}
}			


public static void OpenSearch(String query,ResultSet[] rs){
	// CREATE PROCEDURE OPENSEARCH(COL1 VARCHAR(10000)) PARAMETER STYLE JAVA MODIFIES SQL DATA LANGUAGE JAVA DYNAMIC RESULT SETS 1 EXTERNAL NAME 'uk.gov.dstl.gaiandb.OFS_Elasticsearch.OpenSearch';					
	/*
	 * CREATE TABLE DB_FEDSEARCH_CONFIG(
         GAIAN_NODE_IPV4 VARCHAR(100) NOT NULL PRIMARY KEY,  (e.g. 172.23.31.01 )
         REPO_NAME VARCHAR(100) NOT NULL,           (e.g. "CONTRACTS" - for searching a repository of contracts)
         SEARCHENGINE_URL VARCHAR(100) NOT NULL,    (e.g. http://123.22.32.12:9200)
         SEARCHENGINE_TYPE VARCHAR(100),            (e.g. ELASTICSEARCH)
         VERSION VARCHAR(20),                       (e.g. 5.2.1)
         URL_FIELD VARCHAR(100),                    (e.g. URL)
         DATE_FIELD VARCHAR(100),                   (e.g. dcterms:valid or dc:date)
         TITLE_FIELD VARCHAR(100),                  (e.g. dc:title)
         GEO_FIELD VARCHAR(100)                     (e.g. geo_shape)
         );
        call setltforrdbtable('FEDSEARCH_CONFIG','LOCALDERBY','DB_FEDSEARCH_CONFIG');	
	 */
	
	
		 try {
			 
			 
			Class.forName("org.apache.derby.jdbc.ClientDriver");
			Connection conn=DriverManager.getConnection("jdbc:default:connection");
			
			
			
			String SQL="delete from DB_SEARCH_RESULTS where URL<>'XXX'";
			Statement s=conn.createStatement();
			s.execute(SQL);
			
			
			String host="http://127.0.0.1:9200"; // used for testing when own IP address is dynamic and not in database
			SQL="SELECT REPO_NAME,SEARCHENGINE_URL,VERSION,URL_FIELD,DATE_FIELD,TITLE_FIELD, GEO_FIELD FROM FEDSEARCH_CONFIG WHERE GAIAN_NODE_IPV4='"+getIPv4Address()+"'";
			System.out.println(SQL);
			ResultSet rsc=s.executeQuery(SQL);
			String version="2.1.0";
			String repo_name="unknown";
			if(rsc.next()){
				host=rsc.getString("SEARCHENGINE_URL");
				repo_name=rsc.getString("REPO_NAME");
				version=rsc.getString("VERSION");
				System.out.println("found this node's ipv4 address in GAIAN VIRTUAL TABLE FEDSEARCH_CONFIG");
			} else {
				System.out.println("WARNING: did not find this node's ipv4 address in GAIAN VIRTUAL TABLE FEDSEARCH_CONFIG");
				System.out.println("WARNING: using default search engine url");
			}
			
			System.out.println("SEARCH_ENGINE_URL="+host+"  (note urls need to have a http:// prefix)");
			System.out.println("REPO_NAME="+repo_name);
			host+="/";

		// query is the opensearch format minus the "http://host:port/path?" part, i.e. is of the form: 
		//	q={searchTerms}&pw={startPage?}&bbox={geo:box?}   (format of response is JSON)
		// where {geo:box} is a comma separated list of numbers: minLon, minLat, maxLon, maxLat
		// where {searchTerms} is a URL encoded list of terms (space separated)
		// also to search within a circle specify lat-lon and radius (in metres on earth surface)
			
		//  The following extensions to OpenSearch have been added in OFS:
		// 	&norepo={repoList?}  where repoList is a comma separated list of names of repositories to be excluded from the search
		//	
		//  &noindex=repo1/index1/index2,repo2/index3/index4  (i.e. slash separated list of indexes to ignore on the specified repo name; 
		//  comma separated between repos).
			
		// A separate method (desrcribeElasticRepo) finds all the indexes and types and stores them in a local table
		// called DB_REPO_TEMP_TABLE. Its columns are: IP (of search engine), LEVEL1 (an index) and LEVEL2 (a comma-separated
	    // list of "types" used by this index). 	
			
			String buildQuery="";
			String queryStart="{\"_source\": [$fieldList],\"from\":$entryNo, \"size\":$size, \"query\":{\"bool\":"; 
			String matchClause="{\"must\": {\"match\": { \"_all\": \"$searchTerms\" }}";
			String simpleClause="{\"must\":{\"simple_query_string\":{\"query\": \"$searchTerms\",\"fields\": [\""+TITLE_FIELD+"^5\",\"_all\"],\"default_operator\": \"and\"}}";
			String filterOpen="\"filter\":[";
			String geoEnvelope="{\"geo_shape\":{\""+GEO_FIELD+"\":{\"shape\":{\"type\": \"envelope\",\"coordinates\" : $envelope";
			String geoClose="},\"relation\": \"within\"}}}";
			String timeFromFilter="{\"range\":{\""+DATE_FIELD+"\":{\"gte\":\"$from\"}}}";
			String timeToFilter="{\"range\":{\""+DATE_FIELD+"\":{\"lte\":\"$to\"}}}";
			String timeFromToFilter="{\"range\":{\""+DATE_FIELD+"\":{\"gte\":\"$from\",\"lte\":\"$to\"}}}";
			String filterClose="]";
			String queryClose="}},";
			String highlightClause="\"highlight\":{\"fields\":{\"*\":{\"require_field_match\":false,\"fragment_size\":$fragSize}}}}";
			
			String fieldList="\""+TITLE_FIELD+"\",\""+DATE_FIELD+"\",\""+URL_FIELD+"\"";
		    queryStart=queryStart.replace("$fieldList",fieldList);
			    
			
			
			boolean filterIsOpen=false;
			
			String[] params=query.split("&");
			Properties paramVals=new Properties();
			for (int i=0;i<params.length;i++){
				String[] kv=params[i].split("=");
				if(kv.length>1){paramVals.setProperty(kv[0],kv[1]);}
			}
			
			// CHECK TO SEE IF THIS REPO HAS BEEN EXCLUDED FROM THE SEARCH
			String norepoList=","+paramVals.getProperty("norepo")+",";
			if(norepoList.contains(","+repo_name+",")){
				rs[0]=s.executeQuery("SELECT * FROM DB_SEARCH_RESULTS"); // which will be null
				return;
			}
			
			buildQuery=queryStart;
			
			String pageSize="10";  // 10 is default on Elasticsearch (note this is per search engine in federation)
			if(query.contains("size=")){
				pageSize=paramVals.getProperty("size");
			}
			buildQuery=buildQuery.replace("$size",pageSize);
			
			int entryNo=0;
			if(query.contains("pw=")){  // first page is pw=1
				String startPage=paramVals.getProperty("pw"); // note Elasticsearch does not do page-offset directly
			    entryNo=(Integer.parseInt(startPage)-1)*Integer.parseInt(pageSize);
			}
			
			buildQuery=buildQuery.replace("$entryNo",String.valueOf(entryNo));
			
			
			boolean boolIsOpen=false;
			String searchTerms="";
			if(query.contains("q=")){
				searchTerms=paramVals.getProperty("q");
			//	matchClause=matchClause.replace("$searchTerms",searchTerms);
			//	buildQuery+=matchClause;
				simpleClause=simpleClause.replace("$searchTerms",searchTerms);
				buildQuery+=simpleClause;
				boolIsOpen=true;
			}
			
			String envelope="";
			if(query.contains("bbox=")){
				String minLon="";
				String minLat="";
				String maxLon="";
				String maxLat="";
				String bbox="";
				int x=query.indexOf("bbox=");
				int y=query.indexOf("&",x+5);
				if (y>0){
					bbox=query.substring(x+5,y);
				} else {
					bbox=query.substring(x+5);
				}
				String[] vals=paramVals.getProperty("bbox").split(",");
				minLon=vals[0];
				minLat=vals[1];
				maxLon=vals[2];
				maxLat=vals[3];
				envelope="[["+minLon+","+maxLat+"],["+maxLon+","+minLat+"]]";
				geoEnvelope=geoEnvelope.replace("$envelope",envelope);
				
				if(!filterIsOpen){
					if(boolIsOpen){
						buildQuery+=",";
					} else {
						buildQuery+="{";
						boolIsOpen=true;
					}
					buildQuery+=filterOpen;
					filterIsOpen=true;
					buildQuery+=geoEnvelope+geoClose;
				} else {
					buildQuery+=","+geoEnvelope+geoClose;	
				}
				
			}
			
		    if(query.contains("dtstart=") || query.contains("dtend")){
		    	if (filterIsOpen){
		    		buildQuery+=",";
		    	} else {
		    		if(boolIsOpen){
						buildQuery+=",";
					} else {
						buildQuery+="{";
						boolIsOpen=true;
					}
		    		buildQuery+=filterOpen;
		    		filterIsOpen=true;   
		    	}
		    	if(query.contains("dtstart")){
		    		String from=paramVals.getProperty("dtstart");
		    		if(query.contains("dtend")){
		    			String to=paramVals.getProperty("dtend");
		    			timeFromToFilter=timeFromToFilter.replace("$from", from);
		    			timeFromToFilter=timeFromToFilter.replace("$to", to);
		    			buildQuery+=timeFromToFilter;
		    		} else {
		    			timeFromFilter=timeFromFilter.replace("$from", from);
		    			buildQuery+=timeFromFilter;
		    		}
		       } else if (query.contains("dtend=")){
		    	   String to=paramVals.getProperty("dtend");
		    	   timeToFilter=timeToFilter.replace("$to", to);
	    			buildQuery+=timeToFilter;
		       }
		    	
		    }
            
		    if(filterIsOpen){
		    	buildQuery+=filterClose;
		    }
		    
		    buildQuery+=queryClose+highlightClause;
		 
		    
		    buildQuery=buildQuery.replace("$fragSize", "120");
		    
		    System.out.println(buildQuery);
		    
		    String indexes="";
		    if (paramVals.getProperty("noindex") != null){
		    	
		    	if(!version.startsWith("2.")){indexes="*,";}  // elasticsearch syntax changed sometime after version 2
		    	// version 2 does not permit    /*,-index1/_search
		    	// version 5 requires a *, (or other valid index range) if want to exclude indexes with minus prefix
		    	
		    	// FIND ANY noindex PARAMETERS THAT APPLY TO THIS NODE'S REPO NAME
	    		String noindexes=paramVals.getProperty("noindex");
	    		System.out.println("noindex param="+noindexes);
		    	String[] noindexArray=noindexes.split(",");
		    	for (int i=0;i<noindexArray.length;i++){
		    		String noindex=noindexArray[i];
		    		String[] elems=noindex.split("/");
		    		String repo=elems[0];
		    		if(repo.equals(repo_name)){
			    		for (int j=1;j<elems.length;j++){
			    			indexes+="-"+elems[j]+",";
			    		}
		    		}
		    		indexes=indexes.substring(0,indexes.length()-1)+"/";
		    	}	
		    	
		    	
		    }
		    
			
			HTTP http=new HTTP();
			String es_response=http.postHTTPJSON(host+indexes+"_search", buildQuery);
			System.out.println(es_response);
			System.out.println("**************");
			
			// PUT EACH "HIT" INTO A SEPARATE ROW OF COL1
			
			
			ObjectMapper objectMapper = new ObjectMapper();

			try {

				JsonNode node = objectMapper.readValue(es_response, JsonNode.class);
			    JsonNode hits1 = node.get("hits");
			    int total_hits=hits1.get("total").asInt();
			    JsonNode hitsArray = hits1.get("hits");
			    double maxScore=hits1.get("max_score").asDouble();
			    double score=0;
			    int rating=0; 
			    String snips="";
			    String exclude=paramVals.getProperty("exclude");
			    ArrayList<String> excludeArray=new ArrayList<String>();
			    if (exclude!=null){ excludeArray=splitExclude(exclude);}
			    for (int i=0;i<hitsArray.size();i++){
			    	snips="";
			    	JsonNode hit = hitsArray.get(i);
			    	JsonNode highlight = hit.get("highlight");
			    	Iterator<String> it=highlight.fieldNames();
			    	
			    	
			    	while (it.hasNext() ){
			    		String key=it.next();
			    		if (!key.equals(TITLE_FIELD)){
			    			JsonNode val=highlight.get(key);
			    			int iSnip=0;
			    			while (val.get(iSnip)!=null){
				    			String thisSnip=val.get(iSnip).asText();
				    			thisSnip=handleSingleQuotes(thisSnip);
				    			if (exclude!=null){
				    				for (String thisExclude : excludeArray){
	
					    				if (!thisSnip.contains(thisExclude)){
					    					snips+="<BR>"+thisSnip+"..";
					    				}
				    				}
				    		    } else {	
				    			snips+="<BR>"+thisSnip+"..";
				    		    }
				    			iSnip++;
			    			}
			    		}
			    		
			    	}

			       if (snips.length()>0){	
			    	  
			    	String json = objectMapper.writeValueAsString(highlight);
			    	
			    	score=hit.get("_score").asDouble();
			    	
			    	rating =(int) (score*1000); 
			    	//FORM A URL TO GET THE _SOURCE LATER IF REQUIRED
			       	String index=hit.get("_index").asText();
			    	String type=hit.get("_type").asText();
			    	String id=hit.get("_id").asText();
			    	String URL=host+index+"/"+type+"/"+id;
			    	
			    	JsonNode fieldsNode=hit.get("_source");
			    	String title=URL;
			    	String source_URL="";
			    	String date="  ";
			    	if (fieldsNode!=null){
			    		JsonNode titleNode=fieldsNode.get(TITLE_FIELD);
			    		if (titleNode!=null){
				    		//JsonNode tn=titleNode.get(0);
				    		title=titleNode.asText();
				    		title=handleSingleQuotes(title);
				    	};
				    	JsonNode dateNode=fieldsNode.get(DATE_FIELD);
			    		if (dateNode!=null){
				    		//JsonNode dt=dateNode.get(0);
				    		date=dateNode.asText();
				    	}
			    		JsonNode URLNode=fieldsNode.get(URL_FIELD);
			    		if (URLNode!=null){
				    		//JsonNode dt=dateNode.get(0);
				    		source_URL=URLNode.asText();
				    	}
			    		
			    	}	
			    	snips=snips.replace("\n","&nbsp;");
			        SQL="insert into DB_SEARCH_RESULTS(TITLE,URL,SOURCE_URL,SNIPS,RATING,HIT_COUNT,DATE_STRING,FROM_REPO) VALUES ('"+title+"','"+URL+"','"+source_URL+"','"+snips+"',"+rating+","+total_hits+",'"+date+"','"+repo_name+"/"+index+"/"+type+"')";
			       
			    	System.out.println(SQL);
			    	s.execute(SQL);
			     }	
			    }
			     
			    rs[0]=s.executeQuery("SELECT * FROM DB_SEARCH_RESULTS");
				

			} catch (IOException e) {
			    e.printStackTrace();
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			
			
			System.out.println("caught exception case 2 - "+e.toString());
			    e.printStackTrace(); // remove this later
				String msg = e.getMessage();
			    if (msg == null) {
			        msg = e.getClass().getName();
			    }

			    
			    Connection conn;
				try {
					conn = DriverManager.getConnection("jdbc:default:connection");
					String fullError=msg;
					if (msg.contains("##")){
						String[] errorParts=msg.split("##");
						msg=errorParts[0];
						fullError=errorParts[1];
					} else {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						fullError = sw.toString();
					
					}
					String SQL="insert into DB_SEARCH_RESULTS(TITLE,URL,SOURCE_URL,SNIPS,RATING,HIT_COUNT) VALUES ('"+msg+"','"+"/.../.."+"','"+fullError+"',"+0+","+0+")";
					Statement s=conn.createStatement();
					s.execute(SQL);
					try {
						Thread.sleep(200);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					rs[0]= s.executeQuery("SELECT * FROM DB_SEARCH_RESULTS");
				
				
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				System.out.println("THIS IS AN UNEXPECTED SQL EXCEPTION");
				e1.printStackTrace();
			}
		
		}
	
		
		
	}




// builds an array each element either being a single word from unquoted input text, or;
// a sequence of originally quoted words (with quotation marks removed).  
// Accepts single or double quotes.
private static ArrayList<String> splitExclude(String text){
	text=text.replace("\\\"", "\"");
	ArrayList<String> result=new ArrayList<String>();
	text=text.replace(","," ");
	text=text.replaceAll("[ ]+", "_");
	String[] splitWords=text.split("_");
	boolean quoted=false;
	String word="";
	String quotedWords="";
	for (int i=0;i<splitWords.length;i++){
		word=splitWords[i];
		if(word.startsWith("\"")||word.startsWith("'")){
			quoted=true;
			quotedWords=" "+word.substring(1);
		} else {
			if(word.endsWith("\"")||word.endsWith("'")){
				quotedWords+=" "+word.substring(0,word.length()-1)+" ";
				result.add(quotedWords);
				quoted=false;
			} else {
				if (quoted){
					quotedWords+=" "+word;
				} else {
					result.add(" "+word+" ");
				}
			}
		}
	}
	System.out.println(result);
	return result;
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


private static String handleSingleQuotes(String text){
	String result=text.trim();
	if (result.startsWith("'")){result=result.substring(1);}
	if (result.endsWith("'")){result=result.substring(0,result.length()-1);}
	if (result.contains("'")){result=result.replace("'", "&#39;");}
	return result;
}

}	
