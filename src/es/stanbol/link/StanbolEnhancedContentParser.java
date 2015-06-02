/**
 * 
 */
package es.stanbol.link;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.loader.JsonSettingsLoader;


/**
 * @author suryamani
 * 
 */
public class StanbolEnhancedContentParser {
	
	private static final String ENTITY_LABEL = "entity-label"; 
	private static final String ENTITY_LABEL_LITERAL = "value";
	private static final String ENTITY_LABEL_LANGUAGE = "language";
	private static final String ENTITY_TYPE = "entity-type";
	private static final String ENTITY_CONFIDENCE = "confidence";
	private static final String ENTITY_REFERENCE = "entity-reference";
	static ESLogger logger; 

	public static List<EntityReference> parseStanbolEnhancedContent(InputStream is) throws EntityAnnotationNotFoundException {
		Map<String, String> props = null;
		try {
			props = loadJsonData(is);
		} catch (IOException ioex) {
			throw new EntityAnnotationNotFoundException("Failed to parse the content",ioex);
		}
		
		List<String> list = new ArrayList<String>();
		for(Map.Entry<String, String> e : props.entrySet()) {
			if(e.getValue().contains("EntityAnnotation")) {
				if(e.getKey().split("\\.").length >=4 ){
				list.add(e.getKey());
				}
			}
		}
		
		if(list.isEmpty()) {
			throw new EntityAnnotationNotFoundException("There is no Stanbol enhanced content for rdf:type EntityAnnotation");
		}
		
		if(logger.isInfoEnabled()) {
			logger.info("Stanbol enhanced content has {} EntityAnnotation.", list.size());
		}
		
		
		Set<String> keySet = props.keySet();
		
		List<EntityReference> entityList = new ArrayList<EntityReference>();
		
		for(String s : list) {
			String prfx = getPrfx(getPrfx(s));
			if(prfx == null) {
				continue;
			}
			Map<String,String> entityMap = new HashMap<String, String>();
			for(String s1 : keySet) {
				if(s1.startsWith(prfx)) {
					entityMap.put(s1, props.get(s1));
					if(logger.isDebugEnabled()) {
						logger.debug("Entity has key {} , and value {}", s1,props.get(s1));
					}
				}
			}
			entityList.add(createEntityRef(entityMap));
		}
		
		return entityList;
	}
	
	
	private static String getPrfx(String s) {
		if(s!=null){
			int index = s.lastIndexOf('.');
			String tmp = null;
			if(index != -1) {
				tmp = s.substring(0,index);
				return tmp;
			}
		}
		return null;
	}
	////fise:entity-label, fise:entity-type, fise:entity-reference, fise:entity-confidence
	
	private static EntityReference createEntityRef(Map<String, String> entityMap) {
		EntityReference er = new EntityReference();
		Set<String> entityKeys = entityMap.keySet();
		List<String> typeList = new ArrayList<String>();
		er.setEntityTypeList(typeList);
		
		boolean isSplitLabel=false;
		String literal=null, language= null;
		
		for (String s : entityKeys) {
			if (s.contains(ENTITY_LABEL)) {
				if(s.contains(ENTITY_LABEL_LITERAL)) {
					literal = entityMap.get(s);
					isSplitLabel=true;
				}
				
				if(s.contains(ENTITY_LABEL_LANGUAGE)) {
					language = entityMap.get(s);
					isSplitLabel=true;
				}
				
				if(!isSplitLabel) {
					er.setEntityLabel(entityMap.get(s));
				}
			}

			if (s.contains(ENTITY_REFERENCE)) {
				er.setEntityReference(entityMap.get(s));
			}

			if (s.contains(ENTITY_TYPE)) {
				er.getEntityTypeList().add(entityMap.get(s));
			}

			if (s.contains(ENTITY_CONFIDENCE)) {
				er.setConfidence(Double.valueOf(entityMap.get(s)));
			}
		}
		
		if(isSplitLabel) {
			er.setEntityLabel(literal); //+"@"+language);
		}
		
		return er;
	}

	/**
	 * @return
	 * @throws IOException
	 */
	private static Map<String, String> loadJsonData(InputStream is)
			throws IOException {
		JsonSettingsLoader loader = new JsonSettingsLoader();
		InputStreamReader reader = new InputStreamReader(is, "UTF-8");

		StringBuffer sb = new StringBuffer();
		char[] buffer = new char[1024];

		try {
			for (;;) {
				int rsz = reader.read(buffer, 0, buffer.length);
				if (rsz < 0)
					break;
				sb.append(buffer, 0, rsz);
			}
		} finally {
			try {
				is.close();
			} catch (IOException ex) { }
		}

		Map<String, String> props = loader.load(sb.toString());
		return props;
	}
	
}
