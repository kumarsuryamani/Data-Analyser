package es.bayesian.link;

/**
 * @author suryamani
 *
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

public class ESBayesianRestCommunicator {
	
	private static final List<Character> ALPHA_NUM_LIST = new ArrayList<Character>();
	static {
		String alphanumerics = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		for(char c : alphanumerics.toCharArray()) {
			ALPHA_NUM_LIST.add(c);
		}
	}
	
	public static ESLogger logger = ESLoggerFactory.getLogger(ESLogger.class.getName());
	
	public static Map<String, String> getTweetResponse(String analyserUri, String tweet) {
		if(analyserUri == null || tweet == null) {
			return null;
		}
		
		String tweetData = null;
		try {
			tweetData = new String(tweet.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			tweetData = "";
			logger.error("Unsupported coding for tweet data");
		}
		String data = processTextForBayesianAnalysis(removeUrlFromTweet(tweetData));
		return fetchJsonResponse(URI.create(analyserUri), data);
	}
	
	public static Map<String, String> getRSSResponse(String analyserUri, String rssTitle, String rssDescription) {
		if(analyserUri == null || rssTitle == null || rssDescription == null) {
			return null;
		}
		String rssDesStr = null;
		try {
			rssDesStr = new String(rssDescription.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			rssDesStr = "";
			logger.error("UnsupportedCoding for RSS data: {}", rssDescription);
		}
		
		String data = processTextForBayesianAnalysis(rssTitle)+" "+processTextForBayesianAnalysis(removeHTMLTagsFromDescription(rssDesStr));
		
		return fetchJsonResponse(URI.create(analyserUri), data);
	}
	
	public static Map<String, String> getStringTextResponse(String analyserUri, String text) {
		if(analyserUri == null || text == null) {
			return null;
		}
		String encodedText = null;
		try {
			encodedText = new String(text.getBytes(),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			encodedText = "";
			logger.error("UnsupportedCoding for text data: {}", text);
		}
		
		String data = processTextForBayesianAnalysis(encodedText);
		
		return fetchJsonResponse(URI.create(analyserUri), data);
	}
	
	private static Map<String, String> fetchJsonResponse(URI analyserUri, String data) {

		try {
			if(logger.isInfoEnabled()) {
				logger.info("Data being sent for bayesian analysis: {}", data);
			}
			
			String data1 = URLEncoder.encode(data, "UTF-8");
			StringBuilder reqUrl = new StringBuilder();
			reqUrl.append(analyserUri).append(data1);
			
			HttpGet request = new HttpGet(reqUrl.toString());
			HttpClient httpClient = new DefaultHttpClient();
			httpClient.getParams().setParameter("http.protocol.version", HttpVersion.HTTP_1_0);
			httpClient.getParams().setParameter("http.socket.timeout", new Integer(10000));
			httpClient.getParams().setParameter("http.protocol.content-charset", "UTF-8");
			request.addHeader("accept", "application/json");
			
			if(logger.isInfoEnabled()) {
				logger.info("Request being sent to Bayesian service for analysis: {}", analyserUri);
			}
			HttpResponse response = httpClient.execute(request);

			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				logger.error("Bayesian response failed, error code : {}, error message: {}", response.getStatusLine().getStatusCode(),
						response.getStatusLine().getReasonPhrase());
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader(
					(response.getEntity().getContent()), "UTF-8"));
			
			StringBuilder sb = new StringBuilder();
			String output;
			while ((output = br.readLine()) != null) {
				sb.append(output);
			}
			String content = sb.toString();
			content = content.replaceAll("(\\[|\\]|\")", "");
			String[] tmpArr = content.split("([\\}](\\s)*[,](\\s)*[\\{])");
			Map<String, String> bayesianResponse = new HashMap<String, String>();
			for(String s : tmpArr) {
				s=s.replaceAll("(\\{|\\})", "");
				String[] jsonRecords = s.split(",");
				if(jsonRecords != null && jsonRecords.length >= 2) {
					String analyzerName = jsonRecords[0].substring(indexOfData(jsonRecords[0])+1);
					String result = jsonRecords[1].substring(indexOfData(jsonRecords[1])+1);
					bayesianResponse.put(analyzerName, result);
				}
			}
			httpClient.getConnectionManager().shutdown();
					
			if(logger.isInfoEnabled()) {
				logger.info("JSON Response from Bayesian analyser REST service: {}", bayesianResponse);
			}
			return bayesianResponse;
		} catch (MalformedURLException e) {
			logger.error("Error 1: ", e.fillInStackTrace());
		} catch (IOException e) {
			logger.error("Error 2: ", e.fillInStackTrace());
		}
		if(logger.isInfoEnabled()) {
			logger.info("Failed to fetch JSON Response from Bayesian analyser REST service");
		}
		return null;
	}

	private static String removeHTMLTagsFromDescription(String description) {
		if(description == null || description.trim().length() < 1) {
			return "";
		}
		
		String tmp = description.replaceAll("\\<[^>]*>","");
		if(logger.isInfoEnabled()) {
			logger.info("RSS description after removing html tags:  {}", tmp);
		}
		return tmp;
	}
	
	private static String removeUrlFromTweet(String tweet) {
		
        String commentstr1=tweet;
        String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure|http):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
        Pattern p = Pattern.compile(urlPattern,Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(commentstr1);
        int i=0;
        while (m.find()) {
            commentstr1=commentstr1.replaceAll(m.group(i),"").trim();
            i++;
        }
        
        if(logger.isInfoEnabled()) {
        	logger.info("Tweet post removing urls: {}", commentstr1);
        }
        return commentstr1;
    }
	
	private static String removeHashes(String tweet) {
		String tmp = tweet.replaceAll("#[\\S]*", "");
		if(logger.isInfoEnabled()) {
        	logger.info("Tweet post removing hashes: {}", tmp);
        }
		return tmp;
	}
	
	private static String removeAtSymbol(String tweet) {
		String tmp = tweet.replaceAll("@[\\S]*", "");
		if(logger.isInfoEnabled()) {
        	logger.info("Tweet post removing @data: {}", tmp);
        }
		return tmp;
	}
	
	
	private static String processTextForBayesianAnalysis(String s) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < s.length(); ++i) {
			char ch = s.charAt(i);
			if(ALPHA_NUM_LIST.contains(Character.valueOf(ch))
					|| Character.isWhitespace(ch)) {
				sb.append(ch);
			}
		}
		return sb.toString();
	}
	
	//my test method for Bayesian response analysis
	private static void processBayesianResponse(InputStream in) {
		String str = "[{\"analyzerName\":\"Desire\",\"result\":\"Undecided\"},{\"analyzerName\":\"Growth\",\"result\":\"A\"}]"; /*,
		 {"analyzerName":"Intent","result":"A"},{"analyzerName":"Sentiment","result":"B"},{"analyzerName":"Power","result":"Undecided"},
		 {"analyzerName":"Safety","result":"B"},{"analyzerName":"Freedom","result":"A"},{"analyzerName":"Subscribe","result":"Undecided"}]";*/
		
		String content = str.replaceAll("(\\[|\\]|\")", "");
		String[] tmpArr = content.split("([\\}](\\s)*[,](\\s)*[\\{])");
		for(String s : tmpArr) {
			s=s.replaceAll("(\\{|\\})", "");
			String[] jsonRecords = s.split(",");
			String analyzerName = jsonRecords[0].substring(indexOfData(jsonRecords[0])+1);
			String result = jsonRecords[1].substring(indexOfData(jsonRecords[1])+1);
			System.out.println(analyzerName+" ::: "+result);
		}
	}
	
	private static int indexOfData(String data) {
		return data.indexOf(':');
	}
	
	
}
