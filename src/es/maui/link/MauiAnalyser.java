/**
 * 
 */
package es.maui.link;

/**
 * @author suryamani
 *
 */
public class MauiAnalyser {
	private String vocabulary;
	private String model;
	
	public MauiAnalyser() {}
	
	public MauiAnalyser(String vocabulary, String model) {
		this.vocabulary = vocabulary;
		this.model = model;
	}

	/**
	 * @return the vocabulary
	 */
	public String getVocabulary() {
		return vocabulary;
	}

	/**
	 * @return the model
	 */
	public String getModel() {
		return model;
	}

	/**
	 * @param vocabulary the vocabulary to set
	 */
	public void setVocabulary(String vocabulary) {
		this.vocabulary = vocabulary;
	}

	/**
	 * @param model the model to set
	 */
	public void setModel(String model) {
		this.model = model;
	}
	
	

}
