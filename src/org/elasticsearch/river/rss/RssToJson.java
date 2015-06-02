package org.elasticsearch.river.rss;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import com.sun.syndication.feed.synd.SyndEntry;

import es.stanbol.link.EntityReference;

public class RssToJson
{
  public static XContentBuilder toJson(SyndEntry message, String riverName, String feedname, 
		  Map<String, List<EntityReference>> entities, Map<String, List<String>> mauiTopics, 
		  List<Map<String,String>> bayesianAnalyzedText) throws IOException{
   
	XContentBuilder out = XContentFactory.jsonBuilder().startObject()
    	.field("feedname", feedname).field("title", message.getTitle())
    	.field("link", message.getLink()).field("uri", message.getUri())
    	.field("description", message.getDescription() != null ? message.getDescription().getValue() : null)
    	.field("publishDate", message.getPublishedDate());
    
    if(entities != null) {
    	if(entities.isEmpty()) {
    		out.field("entities", "{}");
    	}else {
    		out.startArray("entities");    
    		for(Map.Entry<String, List<EntityReference>> entityEntry : entities.entrySet()) {
    			out.startObject();
		    	out.startArray(entityEntry.getKey());	
		    	for(EntityReference er : entityEntry.getValue()) {
		    		out.startObject();
		    		out.field("entity-label",er.getEntityLabel());
		    		out.field("entity-reference", er.getEntityReference());
		    		out.field("entity-confidence", er.getConfidence());
		    		out.startArray("entity-type");
		    		for(String s : er.getEntityTypeList()) {
	    				out.startObject();
	    				out.field("type", s);
	    				out.endObject();
	    			}
		    		out.endArray();
		    		out.endObject();
		        }
		    	out.endArray();
		    	out.endObject();
		    }
		    out.endArray();
    	}
    }
    
    if(mauiTopics != null) {
    	if(mauiTopics.isEmpty()) {
    		out.field("topics", "{}");
    	}else {
    		out.startArray("topics");
    		out.startObject();
    		for(Map.Entry<String, List<String>> e : mauiTopics.entrySet()) {
    			out.startArray(e.getKey());
    			for(String s : e.getValue()) {
    				out.startObject();
    				out.field("text", s);
    				out.endObject();
    			}
    			out.endArray();
    		}
    		out.endObject();
    		out.endArray();
    	}
    }
    
    if(bayesianAnalyzedText != null) {
    	if(bayesianAnalyzedText.isEmpty()) {
    		out.field("BayesianAnalysers", "{}");
    	} else{    		
    		out.startArray("BayesianAnalysers");
    		out.startObject();
    		for(Map<String, String> m : bayesianAnalyzedText) {
    			for(Map.Entry<String, String> e : m.entrySet()) {
    				out.startArray(e.getKey());
    				out.startObject();
        			out.field("text", e.getValue());
        			out.endObject();
        			out.endArray();
    			}
    		}
    		out.endObject();
    		out.endArray();    		
    	}
    }
    
    if (riverName != null) {
      out.field("river", riverName);
    }
    return out.endObject();
  }

}