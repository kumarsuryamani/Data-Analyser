/**
 * 
 */
package es.bayesian.link;

/**
 * @author suryamani
 *
 */
public class BayesianAnalyser {
	
	private String analyserName;
	private String analyserUrl;
	
	
	public BayesianAnalyser() {}
	
	public BayesianAnalyser(String analyserName, String analyserUrl) {
		this.analyserName = analyserName;
		this.analyserUrl = analyserUrl;
	}
	
	/**
	 * @return the analyserName
	 */
	public String getAnalyserName() {
		return analyserName;
	}
	
	/**
	 * @return the analyserUrl
	 */
	public String getAnalyserUrl() {
		return analyserUrl;
	}
	/**
	 * @param analyserName the analyserName to set
	 */
	public void setAnalyserName(String analyserName) {
		this.analyserName = analyserName;
	}
	/**
	 * @param analyserUrl the analyserUrl to set
	 */
	public void setAnalyserUrl(String analyserUrl) {
		this.analyserUrl = analyserUrl;
	}

}
