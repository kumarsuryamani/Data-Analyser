/**
 * 
 */
package es.stanbol.link;

/**
 * @author suryamani
 *
 */
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.elasticsearch.common.logging.ESLogger;

public class EsStanbolRestCommunicator {

	private ESLogger logger;
	List<StanbolAnalyser> analyserList;
	
	private static final String OUTPUT_FORMATTER = "outputContent=*";
	
	/*
	 * public static void main(String[] args) throws IOException {
	 * EsStanbolRestCommunicator communicator = new EsStanbolRestCommunicator();
	 * communicator.execute(); }
	 */

	public EsStanbolRestCommunicator(ESLogger logger, List<StanbolAnalyser> analyserList) {
		this.logger = logger;
		if(StanbolEnhancedContentParser.logger == null) {
			StanbolEnhancedContentParser.logger = logger;
		}
		this.analyserList = analyserList;
	}

	// curl -X POST -H "Accept: text/turtle" -H "Content-type: text/plain"
	// --data
	// "The Stanbol enhancer can detect famous cities such as Paris and people such as Bob Marley."
	// http://localhost:8080/enhancer

	public Map<String, List<EntityReference>> getEntity(String data) throws IOException {
		
		//http://dev.iks-project.eu:8081/enhancer?outputContent=*
		
		Map<String, List<EntityReference>> analysedMap = new HashMap<String, List<EntityReference>>();
		String uri = null;
		for(StanbolAnalyser analyser : analyserList) {
			uri = getAnalyserFormattedURI(analyser.getUrl());
			if(logger.isDebugEnabled()) {
				logger.debug("Data being sent to URI {} has content: {} ", uri,data);
			}
			List<EntityReference> entityRef = null;
			if((entityRef=getAnalysedEntity(uri, data)) == null || entityRef.isEmpty()) {
				if(logger.isWarnEnabled()) {
					logger.warn("There is no analysed/enhanced content from the analyser {} for data {} ", analyser.getAnalyzerId(),data);
				}
				continue ;
			}
			analysedMap.put(analyser.getAnalyzerId(), entityRef);
		}
		
		return analysedMap;
		
	} 
	
	private List<EntityReference> getAnalysedEntity(String uri, String data) throws IOException{
		HttpPost request = new HttpPost(uri);
		HttpResponse response = null;
		HttpClient httpClient = new DefaultHttpClient();
		
		if(data == null || data.trim().length() <= 0) {
			return null;
		}
		
		//String htmlText= "<html><body><p>"+data+"</p></body></html>";
		request.setEntity(new StringEntity(data, "UTF-8"));
		request.addHeader("Content-type", "text/plain");
		request.addHeader("Accept", "application/json");
		
		response = httpClient.execute(request);
		
		List<EntityReference> entities = null;
		InputStream is = null;
		try {
			is = response.getEntity().getContent();
			entities = StanbolEnhancedContentParser.parseStanbolEnhancedContent(is);
		} catch (IllegalStateException e) {
			logger.error("Illegal state of response: ", e);
		} catch (EntityAnnotationNotFoundException e) {
			logger.error(e.getMessage());
		} finally {
			is.close();
		}
		return entities;
	}
	
	private String getAnalyserFormattedURI(String uriAsString) {
		URI stanbolURI = URI.create(uriAsString);
		String qryString = stanbolURI.getQuery();
		String uri = null;
		if(qryString == null) {
			uri = stanbolURI.toString()+"?"+OUTPUT_FORMATTER;
		}else if(qryString.length() == 0) {
			uri = stanbolURI+OUTPUT_FORMATTER;
		}else if(qryString.length() > 0) {
			uri = stanbolURI+"&"+OUTPUT_FORMATTER;
		}else {
			uri = stanbolURI.toString();
		}
		return uri;
	}

}
