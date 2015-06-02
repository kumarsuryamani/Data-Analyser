package org.elasticsearch.river.facebook;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import es.stanbol.link.EntityReference;
import facebook4j.IdNameEntity;
import facebook4j.Post;
import facebook4j.internal.org.json.JSONException;
import facebook4j.internal.org.json.JSONObject;

public class FB2Json
{
  public static XContentBuilder toJson(Post post, String riverName, 
		  Map<String, List<EntityReference>> entities, Map<String, List<String>> mauiTopics, 
		  List<Map<String,String>> bayesianAnalyzedText) 
		  throws IOException{
   		XContentBuilder out = XContentFactory.jsonBuilder().startObject();
		out.field("PostId",post.getId());
		out.field("PostLink", post.getLink() != null ? post.getLink().toString() : null);
		out.field("PostCreatedTime",post.getCreatedTime());
		
		IdNameEntity idName = null;
		if((idName = post.getFrom()) != null) {
			out.field("PostFromId",idName.getId());
			out.field("PostFromName",idName.getName());
		}
		if(post.getStatusType() != null) {
			out.field("PostStatusType",post.getStatusType());
		}
		out.field("Post", post.getMessage());
		
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
	    			if(m == null || m.isEmpty()) {
	    				continue ;
	    			}
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
	      out.field("RiverName", riverName);
	    }
	    return out.endObject();
  }

}