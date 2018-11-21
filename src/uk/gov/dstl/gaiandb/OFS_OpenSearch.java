package uk.gov.dstl.gaiandb;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * THIS CLASS IS TO CONNECT OFS TO SEARCH ENGINES THAT SUPPORT THE OPENSEARCH API WITH RSS FORMAT
 */
public class OFS_OpenSearch {

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
