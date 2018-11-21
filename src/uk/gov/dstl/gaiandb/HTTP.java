// Dstl (c) Crown Copyright 2018
package uk.gov.dstl.gaiandb;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.List;


public class HTTP {
	
	public HTTP(){;}
	
	/**
	 * @param unencodedURL - this method does not do URLencoding
	 * @return String (the response from server).
	 * @throws Exception
	 */
	public String getHTTP(String URLString) throws Exception
	{
		byte[] bytes = null;
		
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.connect();	
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode);
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			String msg=e.getMessage();
			throw new Exception(msg);
		}
		String result=new String(bytes,"UTF-8");
		return result;

	}
	/**
	 * @param unencodedURL - this method does URLencoding
	 * @return String (the response from server).
	 * @throws Exception
	 */
	public String getHTTPwithURLencoding(String unencodedURL) throws Exception
	{
		byte[] bytes = null;
		String URLString=fullURLencode(unencodedURL);
		
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.connect();	
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode);
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Exception(e);
		}
		String result=new String(bytes);
		return result;

	}
	/**
	 * @param URL (String) - this method does not do URL encoding.
	 * @param cookie (String) -- cookie to be sent with GET. 
	 * @return response (String) -- the response from server.
	 * @throws Exception
	 */
	public String getHTTP(String URLString, String cookie) throws Exception
	{
		byte[] bytes = null;
	
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.setRequestProperty("Cookie", cookie);
			conn.connect();	
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode+conn.getResponseMessage()); //2017-09-13
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new Exception(e);
		}
		String result=new String(bytes);
		return result;

	}

	/**
	 * @param URL (String) - this method does URL encoding.
	 * @param cookie (String) -- cookie to be sent with GET. 
	 * @return response (String) -- the response from server.
	 * @throws Exception
	 */
	public String getHTTPwithURLencoding(String URLunencoded, String cookie) throws Exception
	{
		byte[] bytes = null;
		String URLString=fullURLencode(URLunencoded);
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.setRequestProperty("Cookie", cookie);
			conn.connect();	
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode);
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			e.getCause().printStackTrace();
			throw new Exception(e);
		}
		String result=new String(bytes);
		return result;

	}
	
	public String postHTTP(String URLString, String data) throws Exception
	{
		byte[] bytes = null;
		
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
			String len=Integer.toString(data.length());
			conn.setRequestProperty("Content-Length", len);
			OutputStream os=conn.getOutputStream();
			os.write(data.getBytes());
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode+conn.getResponseMessage()); // 2017-09-13
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			e.getCause().printStackTrace();
			throw new Exception(e);
		}
		String result=new String(bytes);
		
		return result;

	}
	
	public String postHTTPJSON(String URLString, String data) throws Exception
	{
		byte[] bytes = null;
		HttpURLConnection conn =null;
		try
		{
			URL url = new URL(URLString);

			conn = (HttpURLConnection) (url.openConnection());
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			String len=Integer.toString(data.length());
			conn.setRequestProperty("Content-Length", len);
			OutputStream os=conn.getOutputStream();
			os.write(data.getBytes());
			int returnCode = conn.getResponseCode();
			System.out.println("return code="+returnCode);
			switch (returnCode){
			case 200:	
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				System.out.println(bytes.toString());
				break;
			default: 
				InputStream errorstream = conn.getErrorStream();
				String response = "";
				String line2="";
				BufferedReader br = new BufferedReader(new InputStreamReader(errorstream));

				while ((line2 = br.readLine()) != null) {
				    response += line2;
				}

				throw new Exception("HTTP error code "+returnCode+","+conn.getResponseMessage()+"##"+response); // 2017-09-13
				
			}
			
				
			
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		
		catch (IOException e)
		{
			System.out.println("HTTP caught  "+e.getMessage());
			//e.printStackTrace();
			String msg=e.getMessage();
			throw new Exception(msg);
		}
		
		String result=new String(bytes);
		
		return result;

	}
	
	// HTTP GET WITH A JSON BODY
	public String GETHTTPJSON(String URLString, String data) throws Exception
	{
		byte[] bytes = null;
		
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.setDoOutput(true);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			String len=Integer.toString(data.length());
			conn.setRequestProperty("Content-Length", len);
			OutputStream os=conn.getOutputStream();
			os.write(data.getBytes());
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				//Success.
				InputStream inStream = conn.getInputStream();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				int line;
				while ((line = inStream.read()) != -1) {
					outStream.write(line);
				}
				bytes = outStream.toByteArray();
				inStream.close();
				outStream.close();
				
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode+conn.getResponseMessage()); // 2017-09-13
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			e.getCause().printStackTrace();
			throw new Exception(e);
		}
		String result=new String(bytes);
		
		return result;

	}
	
	/**
	 * @param URLString
	 * @param data  ; data to be sent in XML (typically username, password in XML)
	 * @return String -- cookie, discards any payload data.
	 * @throws Exception
	 */
	public String getCookie(String URLString, String data) throws Exception
	{
		
		try
		{
			URL url = new URL(URLString);

			HttpURLConnection conn = (HttpURLConnection) (url.openConnection());
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
			String len=Integer.toString(data.length());
			conn.setRequestProperty("Content-Length", len);
			OutputStream os=conn.getOutputStream();
			os.write(data.getBytes());
			int returnCode = conn.getResponseCode();
			if (returnCode == 200)
			{
				
				Map<String,List<String>> map=conn.getHeaderFields();
				List<String> cookieList=map.get("Set-Cookie");
				return cookieList.get(0);
			}
			else 
			{
				throw new Exception("HTTP error code "+returnCode);
				
			}
			
		}
		catch (MalformedURLException e)
		{
			throw new Exception("Malformed URL: "+URLString, e);
		}
		catch (IOException e)
		{
			throw new Exception(e);
		}
	}

	/**Converts each parameter of a REST GET URL into URLencoded form
	 * @param fullURL , e.g. http://abc.com/xyz?param1=aaa&param2=bbb ...
	 * @return  converts param values aaa, bbb, etc into URL encoded form
	 */
	@SuppressWarnings("deprecation")
	public static String fullURLencode(String fullURL){
		String codedURL="";
		String[] prefix=new String[20];
		String[] val=new String[20];
		int firstQuestion=fullURL.indexOf("?");
		if (firstQuestion>0){
		String host=fullURL.substring(0,firstQuestion+1);
		fullURL=fullURL.substring(firstQuestion+1);
		int firstEquals=fullURL.indexOf("=");
		int count=0;
		boolean done=false;
		while (!done){
			prefix[count]=fullURL.substring(0,firstEquals);
			String remainder=fullURL.substring(firstEquals+1);
			int firstAmp=remainder.indexOf("&");
			if (firstAmp<0){
			val[count]=remainder;
			done=true;	
			} else {
			val[count]=remainder.substring(0,firstAmp);
			fullURL=remainder.substring(firstAmp+1);
			}
			firstEquals=fullURL.indexOf("=");
			count++;
		}
		codedURL=host;
		boolean first=true;
		for (int i=0;i<count;i++){
			if (first){
				codedURL=codedURL+prefix[i]+"="+URLEncoder.encode(val[i]);
				first=false;
			} else {
				codedURL=codedURL+"&"+prefix[i]+"="+URLEncoder.encode(val[i]);	
			}
			
		}
		System.out.println(codedURL);
		return codedURL;
	} else {
		return fullURL; // no encoding performed if URL has no "?"
	}
	}

}

