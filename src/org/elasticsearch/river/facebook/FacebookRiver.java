package org.elasticsearch.river.facebook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import maui.main.MauiWrapper;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.UUID;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;

import es.bayesian.link.BayesianAnalyser;
import es.bayesian.link.ESBayesianRestCommunicator;
import es.maui.link.MauiAnalyser;
import es.stanbol.link.EntityReference;
import es.stanbol.link.EsStanbolRestCommunicator;
import es.stanbol.link.StanbolAnalyser;
import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.Post;
import facebook4j.ResponseList;
import facebook4j.conf.ConfigurationBuilder;

public class FacebookRiver extends AbstractRiverComponent implements River {
	
	private final Client client;
	private final String indexName;
	private final String typeName;
	
	private int updateRate;
	private List<String> topics;
	
	private String oauthAppId = null;
    private String oauthAppSecret = null;
    private String oauthAccessToken = null;
    
    private FacebookAuthModel authModel;
    
    private Thread facebookPoller;
    	
	private volatile boolean closed = false;
	
	private List<StanbolAnalyser> analyzersList;
	private List<MauiAnalyser> mauiAnalyserList;
	private List<BayesianAnalyser> bayesianAnalyserList;
		
	@Inject
	public FacebookRiver(RiverName riverName, RiverSettings settings, Client client) {
		super(riverName, settings);
		this.client = client;
						
		if(!settings.settings().containsKey("facebook")) {
			this.logger.warn("There is no Facebook river defined...exiting");
			System.exit(0);
		}

        Map<String, Object> facebookSettings = (Map<String, Object>) settings.settings().get("facebook");
        if (facebookSettings.containsKey("oauth")) {
            Map<String, Object> oauth = (Map<String, Object>) facebookSettings.get("oauth");
            if (oauth.containsKey("app_id")) {
                oauthAppId = XContentMapValues.nodeStringValue(oauth.get("app_id"), null);
            }
            if (oauth.containsKey("app_secret")) {
                oauthAppSecret = XContentMapValues.nodeStringValue(oauth.get("app_secret"), null);
            }
            if (oauth.containsKey("access_token")) {
                oauthAccessToken = XContentMapValues.nodeStringValue(oauth.get("access_token"), null);
            }
            this.authModel = new FacebookAuthModel();
            authModel.setoAuthAppId(oauthAppId);
            authModel.setoAuthAppSecret(oauthAppSecret);
            authModel.setoAuthAccessToken(oauthAccessToken);
            authModel.setDebugEnabled(true);
            authModel.setIsJsonStoreEnabled(true);
            authModel.setoAuthPermissions("email,publish_stream");
            
            this.updateRate = XContentMapValues.nodeIntegerValue(facebookSettings.get("update_rate"), 900000);
            
            if(XContentMapValues.isArray(facebookSettings.get("topics"))) {
            	topics = new ArrayList<String>();
            	ArrayList<Map<String, Object>> postTopics = (ArrayList<Map<String,Object>>) facebookSettings.get("topics");
            	for(Map<String, Object> topic : postTopics) {
            		Collection<Object> tValues = topic.values();
            		for(Object o : tValues) {
            			topics.add((String) o);
            		}
            	}
            }
        }else {
        	this.logger.warn("There is no Facebook oauth defined...exiting");
			System.exit(0);
        }
        
        boolean isAnalysersArray = facebookSettings.get("stanbol_analysers") != null && 
        		XContentMapValues.isArray(facebookSettings.get("stanbol_analysers"));
		if (isAnalysersArray) {
			ArrayList<Map<String, Object>> analyzers = (ArrayList<Map<String,Object>>) facebookSettings
					.get("stanbol_analysers");
			this.analyzersList = new ArrayList<StanbolAnalyser>(analyzers.size());
			for (Map<String,Object> analyzer : analyzers) {
				String analyserName = XContentMapValues.nodeStringValue(
						analyzer.get("analyser_name"), null);
				String url = XContentMapValues.nodeStringValue(
						analyzer.get("url"), null);
				this.analyzersList.add(new StanbolAnalyser(analyserName, url));
			}
		}
		
		boolean isMauiAnalysersArray = facebookSettings.get("maui_analysers") != null && 
				XContentMapValues.isArray(facebookSettings.get("maui_analysers"));
		if (isMauiAnalysersArray) {
			ArrayList<Map<String, Object>> mauiAnalyzers = (ArrayList<Map<String,Object>>) facebookSettings
					.get("maui_analysers");
			this.mauiAnalyserList = new ArrayList<MauiAnalyser>(mauiAnalyzers.size());
			for (Map<String,Object> ma : mauiAnalyzers) {
				String mauiVocabName = XContentMapValues.nodeStringValue(
						ma.get("maui_vocabulary_name"), null);
				String mauiModelName = XContentMapValues.nodeStringValue(
						ma.get("maui_model_name"), null);
				this.mauiAnalyserList.add(new MauiAnalyser(mauiVocabName, mauiModelName));
			}
		}
		
		boolean isBayesianAnalysersArray = facebookSettings.get("bayesian_analysers") != null && 
				XContentMapValues.isArray(facebookSettings.get("bayesian_analysers"));
		if (isBayesianAnalysersArray) {
			ArrayList<Map<String, Object>> bayesianAnalyzers = (ArrayList<Map<String,Object>>) facebookSettings
					.get("bayesian_analysers");
			this.bayesianAnalyserList = new ArrayList<BayesianAnalyser>(bayesianAnalyzers.size());
			for (Map<String,Object> ba : bayesianAnalyzers) {
				String analyserName = XContentMapValues.nodeStringValue(
						ba.get("analyser_name"), null);
				String analyserUrl = XContentMapValues.nodeStringValue(
						ba.get("analyser_url"), null);
				this.bayesianAnalyserList.add(new BayesianAnalyser(analyserName, analyserUrl));
			}
		}
		
        if (settings.settings().containsKey("index")) {
            Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
            indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
            typeName = XContentMapValues.nodeStringValue(indexSettings.get("type"), "post");
        } else {
            indexName = riverName.name();
            typeName = "post";
        }
    }

	public void start() {
		if (this.logger.isInfoEnabled())
			this.logger.info("Starting facebook stream...");
		try {
			this.client.admin().indices().prepareCreate(this.indexName)
					.execute().actionGet();
		} catch (Exception e) {
			if (!(ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException)) {
				if (!(ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException)) {
					this.logger.warn(
							"failed to create index [{}], disabling river...",
							e, new Object[] { this.indexName });

					return;
				}
			}
		}

		this.facebookPoller = EsExecutors.daemonThreadFactory(
					this.settings.globalSettings(),"FB-Poller").newThread(new FacebookPoller(authModel));

		this.facebookPoller.start();
	}

	public void close() {
		if (this.logger.isInfoEnabled())
			this.logger.info("Closing Facebook river", new Object[0]);
		if(this.facebookPoller.isAlive()) {
			this.facebookPoller.interrupt();
		}
		this.closed = true;

		
	}

	private List<ResponseList<Post>>  getPosts(Facebook facebook, List<String> topics) {
		List<ResponseList<Post>> postsList = new ArrayList<ResponseList<Post>>(topics.size());
		try {
			for(String topic : topics) {
				ResponseList<Post> postResponseList = facebook.searchPosts(topic);
				postsList.add(postResponseList);
			}
		} catch (FacebookException e) {
			this.logger.error("Error fetching the posts: ", e);
		}
		return postsList;
	}

	private class FacebookPoller implements Runnable {
		private Facebook facebook;

		public FacebookPoller(FacebookAuthModel authModel) {
			ConfigurationBuilder cb = new ConfigurationBuilder();
			cb.setDebugEnabled(authModel.isDebugEnabled())
			  .setOAuthAppId(authModel.getoAuthAppId())
			  .setOAuthAppSecret(authModel.getoAuthAppSecret())
			  .setOAuthAccessToken(authModel.getoAuthAccessToken())
			  .setOAuthPermissions(authModel.getoAuthPermissions());
			FacebookFactory ff = new FacebookFactory(cb.build());
			this.facebook = ff.getInstance();
			
		}

		public void run() {
			while (true) {
				if (FacebookRiver.this.closed) {
					return;
				}
				List<ResponseList<Post>> posts = FacebookRiver.this.getPosts(facebook, FacebookRiver.this.topics);
				if (posts != null && !posts.isEmpty()) {
					boolean allDone=false;
					BulkRequestBuilder bulk = FacebookRiver.this.client.prepareBulk();
					
					for(ResponseList<Post> responseList : posts) {
						if(responseList == null || responseList.isEmpty()) {
							continue ;
						}
						int size = responseList.size();
						for(int i = 0; i < size; ++i) {
							Post p = responseList.get(i);
							String postIdentity = p.getId()+"-"+p.getObjectId();
							String uniqePostId = UUID.nameUUIDFromBytes(postIdentity.getBytes()).toString();
							String lastupdateField = "_lastupdated_"+uniqePostId ;
							
							if(isDuplicatePost(lastupdateField)) {
								allDone=true;
								continue ;
							}
							allDone=false;
							GetResponse oldMessage = (GetResponse) FacebookRiver.this.client
									.prepareGet(FacebookRiver.this.indexName,FacebookRiver.this.typeName, uniqePostId).execute().actionGet();
							if(!oldMessage.isExists()) {
								try {
									Map<String, List<EntityReference>> entities = getStanbolEntityForFBPost(p.getMessage());
									Map<String, List<String>> mauiTopics = getTopicsIndexedForFBPost(p.getMessage());
									List<Map<String,String>> bayesianAnalyzedText = getBayesianAnalyzedTextForFBPost(p.getMessage());
									populateFeed2BulkReques(bulk, p, uniqePostId, entities, mauiTopics, bayesianAnalyzedText);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							try {
								populateLastUpdate2BulkRequest(lastupdateField, bulk);
							} catch (IOException e1) {
								e1.printStackTrace();
							}
						}
						try {
							BulkResponse response = (BulkResponse) bulk.execute().actionGet();
							if (response.hasFailures()) {
								FacebookRiver.this.logger.warn("failed to execute"+ response.buildFailureMessage(),
										new Object[0]);
							}else{
								if (FacebookRiver.this.logger.isInfoEnabled()) {
									FacebookRiver.this.logger.info(
											"No. of posts indexed : {}", Integer.valueOf(size));
								}
							}
						} catch (Exception e) {
							FacebookRiver.this.logger.warn("failed to execute bulk ",e, new Object[0]);
						}
						
					}
					
					if(allDone) {
						if (FacebookRiver.this.logger.isInfoEnabled()) {
							FacebookRiver.this.logger.info(
									"There is no new facebook post...Relaxing");
						}
							
						try {
							if (FacebookRiver.this.logger.isInfoEnabled()) {
								FacebookRiver.this.logger.info("Facebook river is going to sleep for {} ms", 
										Integer.valueOf(FacebookRiver.this.updateRate));
							}
							Thread.sleep(FacebookRiver.this.updateRate);
						} catch (InterruptedException e1) {}
					}
					
				}	
			}
		}

		private void populateLastUpdate2BulkRequest(String lastupdateField, BulkRequestBuilder bulk) throws IOException {
				bulk.add(Requests
					.indexRequest("_river")
					.type(FacebookRiver.this.riverName.name())
					.id(lastupdateField)
					.source(XContentFactory.jsonBuilder()
							.startObject().endObject()));
		}

		private void populateFeed2BulkReques(BulkRequestBuilder bulk,
				Post post, String id, Map<String, List<EntityReference>> entities,
				Map<String,List<String>> mauiTopics, List<Map<String,String>> bayesianAnalyzedText) throws IOException {
			
			if(entities == null || entities.isEmpty()) {
				if(FacebookRiver.this.logger.isWarnEnabled()) {
					FacebookRiver.this.logger.warn("There is no Stanbol content enhancement done for entity Linking...indexing FB Post");
				}
			} 
			
			if(mauiTopics == null || mauiTopics.isEmpty()) {
				if(FacebookRiver.this.logger.isWarnEnabled()) {
					FacebookRiver.this.logger.warn("There is no MAUI topics indexed...indexing FB Post");
				}
			}
			
			if(bayesianAnalyzedText == null || bayesianAnalyzedText.isEmpty()) {
				if(FacebookRiver.this.logger.isWarnEnabled()) {
					FacebookRiver.this.logger.warn("There is no Bayesian analyzed text indexed...indexing FB Post");
				}
			}
			
			XContentBuilder cb = FB2Json.toJson(post,FacebookRiver.this.riverName.getName(),entities, mauiTopics, bayesianAnalyzedText);
			
			if (FacebookRiver.this.logger.isInfoEnabled()) {
				FacebookRiver.this.logger.info("The facebook post document which ll be indexed: {}", cb.string());
			}
			
			bulk.add(Requests
					.indexRequest(FacebookRiver.this.indexName)
					.type(FacebookRiver.this.typeName)
					.id(id)
					.source(cb));
		}

		private boolean isDuplicatePost(String lastupdateField) {
			FacebookRiver.this.client.admin().indices().prepareRefresh(new String[] { "_river" }).execute().actionGet();
			GetResponse lastSeqGetResponse = (GetResponse) FacebookRiver.this.client.prepareGet("_river", 
						FacebookRiver.this.riverName().name(),
						lastupdateField).execute().actionGet();

			if (lastSeqGetResponse.isExists()) {
				return true;
			}

			return false;
	   } 
		
		private Map<String, List<EntityReference>> getStanbolEntityForFBPost(String post) {
			if(FacebookRiver.this.analyzersList == null ||
					FacebookRiver.this.analyzersList.isEmpty()) {
				if (FacebookRiver.this.logger.isInfoEnabled())
					FacebookRiver.this.logger.info("There is no Stanbol analyser defined.");
				return null;
			}
			if (FacebookRiver.this.logger.isInfoEnabled())
				FacebookRiver.this.logger.info("ES-Stanbol communication starting...");
			EsStanbolRestCommunicator stanbolCommunicator = new EsStanbolRestCommunicator(FacebookRiver.this.logger, FacebookRiver.this.analyzersList);
			try {
				return stanbolCommunicator.getEntity(post);
			} catch (IOException e) {
				return null;
			}
		}
		
		private Map<String,List<String>> getTopicsIndexedForFBPost(String post) {
			if(FacebookRiver.this.mauiAnalyserList == null ||
					FacebookRiver.this.mauiAnalyserList.isEmpty()) {
				if (FacebookRiver.this.logger.isInfoEnabled())
					FacebookRiver.this.logger.info("There is no MAUI topic analyser defined.");
				return null;
			}
			if (FacebookRiver.this.logger.isInfoEnabled())
				FacebookRiver.this.logger.info("MAUI topic index requesting...");
			Map<String, List<String>> mauiTopicsByVocabulary = new HashMap<String, List<String>>();
			for(MauiAnalyser ma : FacebookRiver.this.mauiAnalyserList) {
				String key = ma.getVocabulary()+"-"+ma.getModel();
				List<String> topicsList = MauiWrapper.extractTopicsForText(ma.getVocabulary(),  ma.getModel(), post); 
				mauiTopicsByVocabulary.put(key, topicsList);
			}
			return mauiTopicsByVocabulary;
			
		}
		
		private List<Map<String, String>> getBayesianAnalyzedTextForFBPost(String post) {
			if(FacebookRiver.this.bayesianAnalyserList == null ||
					FacebookRiver.this.bayesianAnalyserList.isEmpty()) {
				if (FacebookRiver.this.logger.isInfoEnabled())
					FacebookRiver.this.logger.info("There is no Bayesian analyser defined .");
				return null;
			}
			
			if (FacebookRiver.this.logger.isInfoEnabled())
				FacebookRiver.this.logger.info("Bayesian analyser starting...");
			
			ESBayesianRestCommunicator.logger = FacebookRiver.this.logger;
			List<Map<String, String>> bayesianAnalyzedDataList = new ArrayList<Map<String, String>>();
			for(BayesianAnalyser ba : FacebookRiver.this.bayesianAnalyserList) {
				Map<String, String> bayesianResponse = ESBayesianRestCommunicator.getStringTextResponse(ba.getAnalyserUrl(), post);
				if(bayesianResponse == null) {
					continue;
				}
				bayesianAnalyzedDataList.add(bayesianResponse);
			}
			return bayesianAnalyzedDataList;
			
		}
	}
	
}