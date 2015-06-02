/**
 * 
 */
package es.stanbol.link;

import java.util.List;

/**
 * @author suryamani
 *
 */
public class EntityReference {

	/*
	@subject.5
	@subject.5.entityhub:site = dbpedia
	@subject.5.enhancer:entity-type.5 = owl:Thing
	@subject.5.enhancer:entity-type.1 = dbp-ont:Settlement
	@subject.5.enhancer:entity-type.2 = dbp-ont:City
	@subject.5.enhancer:entity-type.3 = http://www.opengis.net/gml/_Feature
	@subject.5.enhancer:entity-type.4 = dbp-ont:Place
	@subject.5.enhancer:entity-type.0 = dbp-ont:PopulatedPlace
	@subject.5.enhancer:entity-reference = http://dbpedia.org/resource/Paris,_Texas
	@subject.5.dc:relation = urn:enhancement-cfb21234-1861-15e0-9968-3d253cede8d4
	@subject.5.dc:created = 2012-10-15T10:14:05.881Z
	@subject.5.@subject = urn:enhancement-115f65c7-6c2b-4130-28dc-cfd4650d2ed0
	@subject.5.enhancer:entity-label.@literal = Paris, Texas
	@subject.5.enhancer:confidence = 0.1780172
	@subject.5.enhancer:entity-label.@language = en
	@subject.5.enhancer:extracted-from = urn:content-item-sha1-37c8a8244041cf6113d4ee04b3a04d0a014f6e10
	@subject.5.dc:creator = org.apache.stanbol.enhancer.engines.entitytagging.impl.NamedEntityTaggingEngine
	@subject.5.@type.0 = enhancer:Enhancement
	@subject.5.@type.1 = enhancer:EntityAnnotation
	*/
	//fise:entity-label, fise:entity-type, fise:entity-reference, fise:entity-confidence
	
	private String entityLabel;
	private List<String> entityTypeList;
	private String entityReference;
	private double confidence;
	
	/**
	 * @return the entityLabelLiteral
	 */
	public String getEntityLabel() {
		return entityLabel;
	}
	/**
	 * @param entityLabelLiteral the entityLabelLiteral to set
	 */
	public void setEntityLabel(String entityLabel) {
		this.entityLabel = entityLabel;
	}
	
	/**
	 * @return the entityTypeList
	 */
	public List<String> getEntityTypeList() {
		return entityTypeList;
	}
	/**
	 * @param entityTypeList the entityTypeList to set
	 */
	public void setEntityTypeList(List<String> entityTypeList) {
		this.entityTypeList = entityTypeList;
	}
	/**
	 * @return the entityReference
	 */
	public String getEntityReference() {
		return entityReference;
	}
	/**
	 * @param entityReference the entityReference to set
	 */
	public void setEntityReference(String entityReference) {
		this.entityReference = entityReference;
	}
	/**
	 * @return the confidence
	 */
	public double getConfidence() {
		return confidence;
	}
	/**
	 * @param confidence the confidence to set
	 */
	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("entity-label: "+this.entityLabel+"\n");
		sb.append("entity-reference: "+this.entityReference+"\n");
		sb.append("confidence: "+this.confidence+"\n");
		if(this.entityTypeList != null && !this.entityTypeList.isEmpty()) {
			int count=0;
			for( String s : this.entityTypeList) {
				sb.append("entity-type@"+(count++)+": "+s+" \n");
			}
		}
		return sb.toString();
	}
	
}
