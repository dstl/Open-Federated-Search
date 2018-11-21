// Dstl (c) Crown Copyright 2018
package uk.gov.dstl.fedsearch;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GaianHandler {
	
	private String URL;
	private String USER;
	private String PASSWORD;
	public Connection CONN;
	
	// IF NECESSARY CHANGE THESE DEFAULT SETTINGS FOR CONNECTION TO DERBY DATABASE WITHIN GAIANDB
	// USER/PASSWORD MUST MATCH THE VALUES DEFINED IN THE derby.properties FILE IN GAIANDB ROOT INSTALLATION FOLDER.
	// In derby.properties the default is entered as: derby.user.gaiandb=passw0rd; 
	// Where gaiandb is the Username, and passw0rd is the password (note the zero in the password).
	private static final String DERBY_URL = "jdbc:derby://localhost:6414/gaiandb";
	private static final String DERBY_USER = "gaiandb";
	private static final String DERBY_PASSWORD = "passw0rd";
	
	public GaianHandler(String url, String user, String password){
		this.URL=url;
		this.USER=user;
		this.PASSWORD=password;

		connect();
	}
	
	private void connect(){

		try {
			Class.forName("org.apache.derby.jdbc.ClientDriver");
			this.CONN = DriverManager.getConnection(URL, USER, PASSWORD);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public GaianHandler() {
		
		
		this.URL=DERBY_URL;
		this.USER=DERBY_USER;
		this.PASSWORD=DERBY_PASSWORD;
		
		connect();
	}

	public ResultSet queryJDBC (String query, String noTableAction) throws Exception {
		return this.queryJDBC(query,URL, USER, PASSWORD, noTableAction);
	}
	
	
	/**
	 * Executes a Gaian "sub-query"  normally on a physical table or logical suffix _0 (i.e. subquery not further propagated) - thus
	 *  get back the Union of the queries run on every node.
	 * @param query
	 * @param noTableAction
	 * @return
	 * @throws Exception
	 */
	public ResultSet gaianQuery(String query, String noTableAction) throws Exception {
		query=query.replace("'","''"); // escape single quotes with another single quote
		String gaianQuery="SELECT * FROM NEW com.ibm.db2j.GaianQuery('"+query.trim()+"','with_provenance') GQ";
		
		
		System.out.println("GQ="+gaianQuery);
		return this.queryJDBC(gaianQuery,URL, USER, PASSWORD, noTableAction);
		   
	}
	
	public ResultSet queryJDBC (String query, String url, String user, String password, String noTableAction) throws Exception {

		ResultSet resultSet = null;
		boolean done=false;
		int loop=0;
		Class.forName("org.apache.derby.jdbc.ClientDriver");
		
		
    while (!done && loop<5){      
 		try {
			loop++;   
			
			if (this.CONN==null){
				this.CONN= DriverManager.getConnection(url, user, password);
			}
			Statement statement = this.CONN.createStatement();
			resultSet = statement.executeQuery(query);
			done=true;
		} catch (SQLException e) {
			if (e.getSQLState().equals("42X05")){ // table does not exist
			  done=false;	
			} else {
			System.err.println("gaianQuery: failed on query " + query);
			System.out.println("SQLState= "+e.getSQLState());
			e.printStackTrace();
			done=true;
			}
		}
			if (!done){ // do this if Logical Table did not exist in GaianDB on this node (must exist somewhere).
				if (noTableAction.equals("create_tables=true")){
					createTables(query,url,user,password); // throws MIPSException
				} 
		
		}
    }	
    	if (loop>3){System.out.println("GaianDB table not found and not created: "+query);}
		return resultSet;
	}
	
	/**
	 * Creates a Gaian Logical Table, and a regular Derby Table on the local node by extracting the table name from
	 * the input query and doing a query of the GaianDB system table "DERBY_TABLES" to determine
	 * the column names and column data-types. Then creates a local table (with prefix DB_) with
	 * these columns. Then creates a Gaian Logical Table from this new local table (without the DB_ prefix). <BR<BR>
	 *  
	 * @param query <code>String</code> - the input SQL containing the name of the missing table.
	 * @param GAIANDB_URL <code>String</code> - the URL of the local GaianDB node.
	 * @throws MIPSException - will fail if no node has the necessary Logical Table
	 */
	public void createTables(String query, String GAIANDB_URL, String user, String password) throws Exception {
		
		
		String tableName="";
		Pattern pattern=Pattern.compile("(?:from)(?:[\\s]+)([\\S]+)");
		Matcher m=pattern.matcher(query.toLowerCase());
        if (m.find()){
        	tableName=m.group(1);
        } else {
        	throw new Exception("Could not find table name in query: "+query);
        }
        //String query2="select distinct colname,coltype from DERBY_TABLES where tabname='"+tableName+"'";
        String query2="call listlts()";
        ResultSet rs2=queryJDBC(query2,GAIANDB_URL, user, password, "fail");
        String ltDef=getLTDEF(rs2,tableName);
        if (ltDef.length()==0){
        	throw new Exception("No node has a Logical Table called "+tableName+" and so unable to execute query.");
        }
        try{	
        	if (this.CONN==null){
        		this.CONN = DriverManager.getConnection(GAIANDB_URL, user, password);
        	}
			
			String command="CREATE TABLE DB_"+tableName+" ("+ltDef+")";
			Statement s = this.CONN.createStatement();
			s.execute(command);	
			System.out.println("Created table: DB_"+tableName);
			String command2="call setltforrdbtable('"+tableName+"','LOCALDERBY','DB_"+tableName+"')";
			Statement s2 = this.CONN.createStatement();
			s2.execute(command2);
			System.out.println("Created Logical Table in GaianDB.");
			
		}
		catch (SQLException e){
			String SQLState=e.getSQLState();
			if (SQLState.equals("08001")){ 
				System.out.println("Make sure GaianDB is running on port 6415 - use launchGaian6415.bat .");
				throw new Exception("GaianDB not running on port 6415");
			} else if (SQLState.equals("X0Y32")){
				System.out.println("Table "+tableName+" exists.");
			} else {
				throw new Exception(e.getMessage());
			}	
		}
		}
	
	private String getLTDEF(ResultSet rs,String tableName){
	String ltDef="";
	try {
		while(rs.next()){
			String s2=rs.getString(2);
			System.out.println(s2);
			if (rs.getString(2).equals(tableName.toUpperCase())){
				ltDef=rs.getString(3);
				return ltDef;
			}
		}	
		} catch (SQLException e){
			e.printStackTrace();
		}
	return ltDef;
	}

	
	
	
	
	public String execute(String SQL){
		try{
			Statement s = this.CONN.createStatement();
			s.execute(SQL);
			System.out.println(SQL);
			SQLWarning warn=s.getWarnings();
			if (warn==null){
				return "";
			} else {
				return warn.getMessage();
			}						
	  } catch (SQLException e){
			String message=e.getMessage();
			return message;
			
	  }
	}
	

}	