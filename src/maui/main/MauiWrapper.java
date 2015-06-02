package maui.main;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import maui.filters.MauiFilter;
import maui.stemmers.PorterStemmer;
import maui.stemmers.Stemmer;
import maui.stopwords.Stopwords;
import maui.stopwords.StopwordsEnglish;
import maui.vocab.Vocabulary;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;


/**
 * This class shows how to use Maui on a single document
 * or just a string of text.
 * @author alyona
 *
 */
public class MauiWrapper {

	private static final Stemmer stemmer;
	private static final Stopwords stopwords;
	private static String language = "en";
	private static final String dataDir;
	private static final ESLogger MAUI_LOG = ESLoggerFactory.getLogger(MauiWrapper.class.getName());
	
	private static final Map<String, MauiFilter> MAUI_INDEXERS = new HashMap<String, MauiFilter>();
	
	
	static {
		if(MAUI_LOG.isInfoEnabled()) {
			MAUI_LOG.info("MAUI model extractors getting loaded....");
		}
		dataDir = System.getProperty("es.path.home")+System.getProperty("file.separator")+"maui_data"+System.getProperty("file.separator");
		stemmer = new PorterStemmer();
		String englishStopwords = dataDir + "stopwords/stopwords_en.txt";
		stopwords = new StopwordsEnglish(englishStopwords);
		//lets pre-configure 3 vocabularies with models
		/*
            "maui_vocabulary_name" "maui_model_name" 	Significance
            "agrovoc_en"			"fao30"			  		Agriculture
            "mesh"					"nlm500"				Medicine
            "hep"					"cern290"				Physics
            "lcsh"					"theses80"				General
            
        */
		Map<String,String> vocab_model = new HashMap<String, String>();
		vocab_model.put("agrovoc_en", "fao30"); //agriculture
		vocab_model.put("mesh", "nlm500");		//medicine
		vocab_model.put("hep", "cern290");		//physics
		vocab_model.put("lcsh", "theses80");	//general
		for(Map.Entry<String, String> e : vocab_model.entrySet()) {
			new MauiWrapper(null, e.getKey(), e.getValue());
		}
		if(MAUI_LOG.isInfoEnabled()) {
			MAUI_LOG.info("MAUI model extractors successfully loaded for General,Agriculture, Physics and Medicine topics indexing.");
		}
	}
	
	/**
	 * Constructor, which loads the data
	 * @param dataDirectory - e.g. Maui's main directory (should has "data" dir in it)
	 * @param vocabularyName - name of the rdf vocabulary
	 * @param modelName - name of the model
	 */
	MauiWrapper(String dataDirectory, String vocabularyName, String modelName) {
		String vocabularyDirectory = dataDir +  "vocabularies/";
		String modelDirectory = dataDir +  "models";
		Vocabulary vocabulary = loadVocabulary(vocabularyDirectory, vocabularyName);
		MauiFilter extractionModel = loadModel(modelDirectory, modelName, vocabularyName, vocabulary);
		String model_id = vocabularyName+"-"+modelName;
		MAUI_INDEXERS.put(model_id, extractionModel);
		if(MAUI_LOG.isInfoEnabled()) {
			MAUI_LOG.info("Vocabulary [{}]with Model [{}] loaded for MAUI model extraction.", vocabularyName, modelName);
		}
	}

	/**
	 * Loads a vocabulary from a given directory
	 * @param vocabularyDirectory
	 * @param vocabularyName
	 */
	private Vocabulary loadVocabulary(String vocabularyDirectory, String vocabularyName) {
		try {
			Vocabulary vocabulary = new Vocabulary(vocabularyName, "skos", vocabularyDirectory);
			vocabulary.setStemmer(stemmer);
			vocabulary.setStopwords(stopwords);
			vocabulary.setLanguage(language);
			vocabulary.initialize();
			return vocabulary;
		} catch (Exception e) {
			MAUI_LOG.error("Failed to load vocabulary!", e);
		}
		return null;
	}
	
	/**
	 * Loads the model
	 * @param modelDirectory
	 * @param modelName
	 * @param vocabularyName
	 */
	private MauiFilter loadModel(String modelDirectory, String modelName, String vocabularyName, Vocabulary vocabulary) {
		MauiFilter extractionModel = null;
		try {
			BufferedInputStream inStream = new BufferedInputStream(
					new FileInputStream(modelDirectory + "/" + modelName));
			ObjectInputStream in = new ObjectInputStream(inStream);
			extractionModel = (MauiFilter) in.readObject();
			in.close();
		} catch (Exception e) {
			MAUI_LOG.error("Failed to load model!", e);
			return null;
		}

		extractionModel.setVocabularyName(vocabularyName);
		extractionModel.setVocabularyFormat("skos");
		extractionModel.setDocumentLanguage(language);
		extractionModel.setStemmer(stemmer);
		extractionModel.setStopwords(stopwords);

		extractionModel.setVocabulary(vocabulary);
		return extractionModel;
	}

	/**
	 * Main method to extract the main topics from a given text
	 * @param text
	 * @param topicsPerDocument
	 * @return
	 * @throws Exception
	 */
	static ArrayList<String> extractTopicsFromText(MauiFilter extractionModel, String text, int topicsPerDocument) throws Exception {

		if (text.length() < 5) {
			throw new Exception("Text is too short!");
		}

		extractionModel.setWikipedia(null);

		FastVector atts = new FastVector(3);
		atts.addElement(new Attribute("filename", (FastVector) null));
		atts.addElement(new Attribute("doc", (FastVector) null));
		atts.addElement(new Attribute("keyphrases", (FastVector) null));
		Instances data = new Instances("keyphrase_training_data", atts, 0);

		double[] newInst = new double[3];

		newInst[0] = (double) data.attribute(0).addStringValue("inputFile");
		newInst[1] = (double) data.attribute(1).addStringValue(text);
		newInst[2] = Instance.missingValue();
		data.add(new Instance(1.0, newInst));

		extractionModel.input(data.instance(0));

		data = data.stringFreeStructure();
		Instance[] topRankedInstances = new Instance[topicsPerDocument];
		Instance inst;

		// Iterating over all extracted keyphrases (inst)
		while ((inst = extractionModel.output()) != null) {

			int index = (int) inst.value(extractionModel.getRankIndex()) - 1;

			if (index < topicsPerDocument) {
				topRankedInstances[index] = inst;
			}
		}

		ArrayList<String> topics = new ArrayList<String>();

		for (int i = 0; i < topicsPerDocument; i++) {
			if (topRankedInstances[i] != null) {
				String topic = topRankedInstances[i].stringValue(extractionModel
						.getOutputFormIndex());
			
				topics.add(topic);
			}
		}
		extractionModel.batchFinished();
		MAUI_LOG.info("MAUI Analysed Topics........");
		MAUI_LOG.info(topics.toString());
		MAUI_LOG.info(".............................");
		return topics;
	}

	public static ArrayList<String> extractTopicsForText(String vocabularyName, String modelName, String text) {
		
		MAUI_LOG.info("[ MAUI Analyser: 'Vocabulary Name' = "+vocabularyName+", 'Model Name' = "+modelName+", 'Text' = "+text+"  ]");
		
		String mauiIndexerKey = vocabularyName+"-"+modelName;
		if(!MAUI_INDEXERS.containsKey(mauiIndexerKey)) {
			new MauiWrapper(dataDir, vocabularyName, modelName);
		}
		MauiFilter modelExtractor = MAUI_INDEXERS.get(mauiIndexerKey);
		try {
			return extractTopicsFromText(modelExtractor, text, 15);
		} catch (Exception e) {
			MAUI_LOG.error("Failed to extract text...", e);			
		}
		return null;
	}
	
	/**
	 * Main method for testing MauiWrapper
	 * Add the path to a text file as command line argument
	 * @param args
	 */
	public static void main(String[] args) {
		
		String vocabularyName = "lcsh";
		String modelName = "theses80";
				
		try {
			String text = "India is a country in South Asia..culturally very rich";
			Runtime rt = Runtime.getRuntime();
			long memAvailable = rt.freeMemory();
			long memAvailMB = (memAvailable)/(1024 * 1024);
			System.out.println("Intial mem available: "+memAvailMB);
			ArrayList<String> keywords = extractTopicsForText(vocabularyName, modelName, text);
			for (String keyword : keywords) {
				System.out.println("Keyword: " + keyword);
			}
			
			long nowAvailable = rt.freeMemory();
			long nowAvailMB = (nowAvailable)/(1024 * 1024);
			System.out.println("Now mem available: "+nowAvailMB);
			
			System.out.println("Memory used: "+(nowAvailMB - memAvailMB));
			
			Thread t = new Thread() {
				public void run() {
					while(true) {
						long nowAvailable = Runtime.getRuntime().freeMemory();
						long nowAvailMB = (nowAvailable)/(1024 * 1024);
						System.out.println("Now mem available: "+nowAvailMB);
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			};
			t.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
