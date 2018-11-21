// Dstl (c) Crown Copyright 2018
package uk.gov.dstl.fedsearch;


public class Hit implements Comparable{
	public String URL="";
	public String snips="";
	public int score=0;
	public int total_hits=0;
	public String repoName="";
	public String title="";
	public String source_URL="";
	public String date_string="";
	
   public Hit(String title, String URL, String source_URL, String snips, int score, int total_hits, String repoName, String date){
	   this.URL=URL;
	   this.snips=snips;
	   this.score=score;
	   this.total_hits=total_hits;
	   this.repoName=repoName;
	   this.title=title;
	   this.source_URL=source_URL;
	   this.date_string=date;
   }
   
 

@Override
public int compareTo(Object o) {
	Hit h= (Hit) o;	
    if (this.score > h.score) {return -1;};
    if (this.score < h.score) {return 1;}
    if (this.URL.compareTo(h.URL)!=0){
    	return 1;
    } else {
    return 0;
    }
}


}
