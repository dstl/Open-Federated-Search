# Open Federated Search (OFS)

[TOC]

## 1. Overview

Open Federated Search (OFS) is designed to search for information on a private network i.e. on networks that are not indexed by Internet search engines such as Google. OFS is particularly designed for use on large private networks that have muliple searchable repositories - enabling all such repositories to be searched at once, even though a range of different search engines and search protocols may be in use. 

Two capabilities are provided:

- a web-application for users to do Federated Searching from a web-browser;
- a REST service, compliant with the OpenSearch specification (with extensions as detailed below), enabling automated data-processsing systems to do Federated Searching.

This system uses the [GaianDB](https://github.com/gaiandb/gaiandb) to create a Federated system. The GaianDB is used to distribute search requests (in [OpenSearch](https://github.com/gaiandb/gaiandb) format) to all the search engines, and to aggregate the results. A GaianDB node is associated with each search engine. A plug-in (in the form of 2 stored procedures) is inserted into each of these GaianDB nodes which sends and receives requests to the associated search engine, converting the requests into the appropriate syntax/protocol; and converting the results into a SQL ResultSet which the GaianDB system can aggregate with the results from other search engines. 

Any web-site that fronts a repository of documents can advertise that its document repository can be searched via an instance of this Open Federated Search system by following the [OpenSearch specification](http://www.opensearch.org/Specifications/OpenSearch/1.1). This defines how to insert a special HTML tag called "link" with an attribute of rel="search" onto the site's home page. This "link" provides the URL of the [OFS OpenSearch description document](https://github.com/dstl/OFS/OpenSearch-Description-Document) (in XML). Compatible web-browsers, such as Google Chrome, know how to interpret such links and enable users to send the search terms that they type into the browser's search bar to OFS instead of sending them to the normal Internet search engine.  Chrome also supports "tabbed browsing", such that the user can type the name of an OpenSearch compatible web-site and then press tab to search it (or assign a control-key to a site then press tab). Chrome also automatically learns which sites have OpenSearch capability and stores them in the user's search settings (where you can add a keyboard short cut, or provide a nickname for the site.)

It is not necessary for the search engines forming this Federation to support OpenSearch. In this initial release a plug-in is provided to enable mulitple [Elasticsearch](https://github.com/gaiandb/gaiandb) repositories to be searched (which has its own proprietary query/response syntax). Another plug-in will be provided soon to interface with any site that supports the OpenSearch API.  It is hoped that other developers will develop plug-ins for other search engines. Guidance for plug-in developers is provided on the project's wiki site.

The Open Federated Search REST service can provide results in RSS, JSON or HTML.  The RSS results are compliant with the OpenSearch specification. The full API of the OFS REST service is provided on this [project's wiki site](https://gihub.com/dstl/OFS/wiki/RES-API).




## 2. Installation
### 2.1 Prerequisites  

1. Required Java (tested with Java 8 but should also work with Java 7).
2. Install [Tomcat](https://tomcat.apache.org/) (tested with Tomcat 7) on each machine that is to host the OFS web-app and REST service.
3. Install [GaianDB](https://github.com/gaiandb/gaiandb) on each machine that will be associated with a particular search engine, and on the machine(s) that will host the OFS web-app. (If the search engines are under your control then install GaianDB on the same machine as each search engine - this will improve performance.)
4. Download jackson-databind-2.8.5.jar from [here](http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/) and jackson-core-2.8.8.jar from [here](http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/) and jackson-annotations-2.8.8.jar from [here](http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/).
5. On each machine that put the above 3 jars in the gaianDB file system in the .../lib folder (same place as derby.jar etc.). 
6. Later you will need a tool to do some Derby database configuration. GaianDB ships with the Derby ij client (in derbytools.jar). It is easier to use a Database Management tool such as [DBeaver](https://dbeaver.io/). 

### 2.2. Install and configure search engine plug-in(s) 

1. In this initial release the only search engine supported is Elasticsearch. Another plug-in for search engines supporting the OpenSearch API is coming soon.  To install the Elasticsearch plug-in, download OPENSEARCH_ES-1.0.0-master.jar from this GiHub repository (on the releases tab), or clone the repository and create the jar yourself. This jar contains two stored procedures called "DESCRIBE_REPO" and "OPENSEARCH", which need to be installed into the Derby Database that the GaianDB uses. To do this, first put the file OPENSEARCH_ES-1.0.0-snapshot.jar into the folder $gaian_home/lib where $gaian_home is the root of the gaiandb installation folder.

2. Now start GaianDB on this node. To do this, in a console window, navigate to the $gaian_home folder, then execute the command "launchGaianServer.sh" (on linux) or "launchGaianServer.bat" (on Windows).

3. Next install the two stored procedures using a suitable database management tool (such as [DBeaver)](https://dbeaver.io/). Connect to the Derby database within the GaianDB on this node using:    

4. ```
   connect 'jdbc:derby://localhost:6414/gaiandb;create=false'  
   
   The username is: 'gaiandb'  (which is also the database/schema name as shown above)
   The default password is:   'passw0rd'    (note zero in place of letter o)
   
   Use the following driver: org.apache.derby.jdbc.ClientDriver, which is in folder $gaian_home/lib/derbyclient.js  
   ```

    Then create install the stored procedures using the following two SQL commands:

   ```
   CREATE PROCEDURE DESCRIBE_REPO(COL1 VARCHAR(10000)) PARAMETER STYLE JAVA MODIFIES SQL DATA LANGUAGE JAVA DYNAMIC RESULT SETS 1 EXTERNAL NAME 'uk.gov.dstl.gaiandb.OFS_Elasticsearch.describeRepo';	
   ```

   ```
   CREATE PROCEDURE OPENSEARCH(COL1 VARCHAR(10000)) PARAMETER STYLE JAVA MODIFIES SQL DATA LANGUAGE JAVA DYNAMIC RESULT SETS 1 EXTERNAL NAME 'uk.gov.dstl.gaiandb.OFS_Elasticsearch.OpenSearch';	
   ```

5. The above stored procedure uses some local database tables both for configuration and as working areas to produce results. Create these tables as follows: 

```
CREATE TABLE DB_FEDSEARCH_CONFIG(
GAIAN_NODE_IPV4 VARCHAR(100) NOT NULL PRIMARY KEY,
REPO_NAME VARCHAR(100) NOT NULL,
SEARCHENGINE_URL VARCHAR(100) NOT NULL,
SEARCHENGINE_TYPE VARCHAR(100) NOT NULL,
VERSION VARCHAR(20) NOT NULL,
URL_FIELD VARCHAR(100),
DATE_FIELD VARCHAR(100),
TITLE_FIELD VARCHAR(100), 
GEO_FIELD VARCHAR(100) 
);
```

```
CREATE TABLE GAIANDB.DB_REPO_TEMP_TABLE (
	REPO_NAME VARCHAR(100) NOT NULL,
	LEVEL1 VARCHAR(100) NOT NULL,
	LEVEL2 VARCHAR(100) NOT NULL,
	CONSTRAINT PK_REPO PRIMARY KEY (REPO_NAME,LEVEL1,LEVEL2)
);
```

```
CREATE TABLE GAIANDB.DB_SEARCH_RESULTS (
	TITLE VARCHAR(500),
	URL VARCHAR(500),
	SOURCE_URL VARCHAR(500),
	SNIPS VARCHAR(5000),
	RATING INTEGER,
	HIT_COUNT BIGINT,
	DATE_STRING VARCHAR(30),
	FROM_REPO VARCHAR(100)
);
```

6. Then create a Gaian Virtual Table called FEDSEARCH_CONFIG, and map it to the local physical table to it as follows:

```
call setltforrdbtable('FEDSEARCH_CONFIG','LOCALDERBY','DB_FEDSEARCH_CONFIG');
```

7. Each node will obtain its own configuration data by querying the Gaian Virtual Table "FEDSEARCH_CONFIG".  It first obtains its own IPv4 address by an operating system call, then uses this as the key to obtain the requisite row of configuration data from this Gaian Virtual Table.  This enables the configuration data of all the nodes to be set-up and managed from any node in the system that has a table called DB_FEDSEARCH_CONFIG.   There can be one central table, or multiple tables, which can each contain complementary or replica data - if there are duplicate entries (by accident) the first one found having the correct IP address will be used.   

8. One row of data needs to be entered into a DB_FEDSEARCH_CONFIG table for each node that is  connected to search engine.  The columns of earch row of this table need the following contents:

   ```
   GAIAN_NODE_IPV4   -- the IPv4 address of a given GaianDB node
   REPO_NAME         -- the name of the repository being searched (users see this)
   SEARCHENGINE_URL  -- the URL of the search engine -- e.g. http://localhost:9200
   SEARCHENGINE_TYPE -- type of search engine -- e.g. Elasticsearch
   VERSION           -- the version of the search engine -- e.g. 5.2.1
   URL_FIELD         -- the name of the field used by the search engine to store the
                        URL of the document that it indexed (used to retrieve
                        the original document) -- e.g. URL
                     
   DATE_FIELD        -- the name of the field used by the search engine to store a date
                        or date-time associated with the document -- e.g. dcterms:valid
                     
   TITLE_FIELD       -- the name of the field used by the search engine to store the
                        title of the document that it indexed -- e.g. dc:title
                        
   GEO_FIELD         -- the name of the field used by the search engine to store the
                        title of the document that it indexed -- e.g. geo_shape
   ```

   For further information regarding the search engine fields see the  [section on Repository Meta-Data Fields](#Repository-Meta-Data-Fields).

​      (The other two tables are used internally and do not need any configuration.)



### 2.3 Install the OFS Tomcat servlets

1. Download OFS-1.0.0-master.war from this repository (on the releases tab) and put it in the Tomcat file system in the $tomcat_home/webapps folder.  (Or clone the repository into Eclipse and export as war file. Note in this case you will first need to add the three Jackson jars (see [prerequisites](#2.1-Prerequisites) item 4) to the WEB-INFO/lib  folder.)

2. Create a simple configuration table within the Derby datatbase that is within the GaianDB system on the node that is hosting the Tomcat server, as follows:

   ```
   CREATE TABLE DB_OFS_CONFIG (
   IPV4 VARCHAR(100) NOT NULL PRIMARY KEY,
   SYSTEM_NAME VARCHAR(100),
   ADMIN_EMAIL VARCHAR(100)
   );
   ```

3. Insert a row into the table, providing the correct IPv4 address for this node and values for "SYSTEM_NAME" and "ADMIN_EMAIL". These values will appear in the RSS messages which the REST service can produce.  The title of the RSS channel will be "Open Federated Search on <SYSTEM_NAME>" (followed by the search terms used). The <SYSTEM_NAME> and <ADMIN_EMAIL> will be inserted into the OpenSearch Description document. This document is hosted on this server. 

4. Ideally a DNS should be provided on the host network. If so record the host-name and IPv4 address of the Tomcat server, and of each repository being searched (but not their associated GaianDB nodes).

   

## 3. Start-up

1. Start GaianDB on the Tomcat server node and on each node that has been provided with the OFS stored procedures in order that each of these nodes may act as a gateway to some particular search engine. 

2. It is helpful to check that the GaianDB nodes have automatically discovered each other. The GaianDB system comes with a tool called dashboard.sh (for linux) or dashboard.bat (for Windows) - this is located in the top-level distribution directory. Running this tool will display the links that have been formed between the GaianDB nodes in the network -- check you have the right number of them! 

3. There may well be barriers on your network that prevent communication between the GaianDB nodes. You will need to ensure that any firewalls allow outgoing connections and permit incoming connections from  each node in the system (the default port is 6414 but this can be changed).  Autodiscovery relies on IP-Multicast, which may not be supported on your network. If all the nodes are on the same LAN segment this will work without any configuration. In other cases (without IP-multicast) you will need to define a gateway node on each remote LAN segment, which will then broadcast the GaianDB discovery packets to all the hosts on its LAN. This network configuration is done via the gaiandb_config.properties file (in the root distribtion folder). Instructions are provided both within that file and in the extensive [GaianDB documentation](https://github.com/gaiandb/gaiandb#contents359), which includes trouble-shooting tips. 

4. Start the Tomcat server that is hosting the OFS web-app and OFS REST service. If it was already running stop and re-start it. 

5. Test the web-app by opening a web-browser and navigating to:

   ```
   http://{tomcatHost}:8080/OFS/FedSearch
   
   where {tomcastHost} is the hostname or IPv4 adddress of the Tomcat server.
   ```

   

6. This should produce the Open Federated Search web-form into which you enter search terms.  The displayed web-form should also show a table of all the search repositories that have been automatically discovered by the Open Federated Search system. Check-boxes against each repository enable their contents to be excluded from a search - see the on-line help link for more details of advanced searching. 

7. The REST service is not intended for use from web-browsers but it can be conveniently tested from a web-browser by navigating to:

   ```
   http://{tomcatHost}:8080/OFS/REST?q={searchTerm}&format=html   (for HTML results)
   
   http://{tomcatHost}:8080/OFS/REST?q={searchTerm}&format=rss   (for RSS 2.0 results)
   
   http://{tomcatHost}:8080/OFS/REST?q={searchTerm}&format=json   (for JSON results)
   
   where {tomcastHost} is the hostname or IPv4 adddress of the Tomcat server, and
         {searchTerms} are one or more terms to search for, terms separate by "+" (i.e. a space with URL encoding), e.g. hello+world.
   ```

   

   The full API of the OFS REST service is provided on the project's wiki page.

   

## 4. Repository Meta-Data Fields

In order for this Open Federated Search Sytem to work properly it is necessary for each repository being searched to have certain basic meta-data. What the fields are called in each repository does not matter, only that the  OPENSEARCH stored procedure knows what they are and can request to be provided with the following information about each indexed document:

- a URL that the end-user can use to access the source document - highly desirable - see note 1.
- the title of the document - highly desirable - see note 2.
- a date associated with the document (see note 3) - recommended - required for data range searches
- a geo-shape (point, polygon, linestring etc) - required for geo-searches only - see note 4.

Note 1: If a URL field is not provided to access the source document (i.e. to link to a pdf, Word document etc.) then the search engine must provide the meta-data needed to access the extracted text which was indexed by the search engine (and the OPENSEARCH stored procedure will compute the full URL from this meta-data). 

Note 2: If no title meta-data field is provided the OPENSEARCH stored procedure will insert the URL as the title. 

Note 3: Many documents have multiple date-related meta-data terms, and different types of document and ETL process may generate yet more terms. Which one should be used is system design question which is partly related to what is most useful to the user, tempered with having to accept a choice between what is actually available. There are two basic needs that the user might have in relation to date-constrained searches:

1. To want to see information that was input into the system within a certain date-range.
2. To want to see information that "relates to" a certain date-range.

The first case may be useful in situations where users regularly perform the same searches every day or at other intervals and want to know what has been added since they last looked. To do this, as each document is indexed,  an ETL process will simply apply the current time-stamp as the chosen meta-data term within the index. 

The second case may be useful to find information about events that occurred in a defined time-frame (irrespective of when these were reported, and irrespective of when the respective documents were ingested into the search index).  In this case the ETL process will need to a suitable meta-data term in a document (such as "dcterms:modified"), and may need to support several different terms used in different document types. It is possible that some types of document will have no suitable meta-data terms that can be extracted in the ETL process - in this case it is a design choice as to whether to leave the value blank or insert as a default the current timestamp (of indexing). The latter would enable the record  to be retrieved in time-bounded queries - but will have the wrong date-semantics. The former would result in the respective record never being returned by any time-bounded search. Neither is ideal. Contrast this with the first case (indexing timestamp), which can be reliably established for all records.

Note 4: The current release of the OFS web-application does not provide an interface through which a geo-query may be defined. (Ideally this would be done by drawing a bounding box on a map.) However, the OFS REST service does implement geo-searches through "bounding boxes" - [see the OpenSearch geo-syntax section](#Geo-search-extension).

The names of these 4 fields need to be configured for each repository, using a database table, as explained in [section 2.2](#2.2.-Install-and-configure-the-requisite-search-engine-plug-in)

If you are trying to set-up a federated search over an existing indexed repository then you will needed to  use whatever terms the repository uses for these meta-data elements (noting that not all are needed, but the first three are highly desirable). If the repository does not have an appropriate field, enter the value "undefined" as the field name in the configuration table.  

If you are setting up a new Elasticsearch repository you can insert your choice of these field-names into the search index as part of your ETL process, and then configure the OFS system to match. In this case it is recommended that "dcterms:valid" be used for the DATE_FIELD. This is preferred because it is not one of the many date-related terms that you would expect to find already attached to a document, and so this enables the ETL process to pick one or more meta-data terms from among the set of date-terms that are attached to a corpus of documents according to which have the required semantics (case 1 or case 2 above); and then copy that value into a new key-value pair, such that all documents in the index have the same key ("dcterms:valid") with the same semantics. 



## 5. Project Wiki

See the [project wiki](https://github.com/dstl/OFS/wiki)  for futher information including:

- [The REST service API](https://github.com/dstl/OFS/wiki/REST-API).
- [The OpenSearch Description Document](https://github.com/dstl/OFS/OpenSearch-Description-Document)  (in XML).
- [Guidance for developing plug-ins for other search engines](https://github.com/dstl/wiki/Development-Guide).



## 6. Licence

Dstl &copy; Crown Copyright 2018.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this software except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License


