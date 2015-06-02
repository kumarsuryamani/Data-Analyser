/**
 * 
 */
package es.stanbol.link;

/**
 * @author suryamani
 *
 */
public class StanbolAnalyser {
	
	private String analyzerId;
	private String url;
	
	public StanbolAnalyser(){}
	
	public StanbolAnalyser(String analyzerId, String url) {
		this.analyzerId = analyzerId;
		this.url = url;
	}
	/**
	 * @return the analyzerId
	 */
	public String getAnalyzerId() {
		return analyzerId;
	}
	/**
	 * @param analyzerId the analyzerId to set
	 */
	public void setAnalyzerId(String analyzerId) {
		this.analyzerId = analyzerId;
	}
	/**
	 * @return the uri
	 */
	public String getUrl() {
		return url;
	}
	/**
	 * @param uri the uri to set
	 */
	public void setUrl(String url) {
		this.url = url;
	}

}
