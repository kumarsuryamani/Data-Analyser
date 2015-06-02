package org.elasticsearch.river.rss;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
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
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import es.bayesian.link.BayesianAnalyser;
import es.bayesian.link.ESBayesianRestCommunicator;
import es.maui.link.MauiAnalyser;
import es.stanbol.link.EntityReference;
import es.stanbol.link.EsStanbolRestCommunicator;
import es.stanbol.link.StanbolAnalyser;

public class RssRiver extends AbstractRiverComponent implements River {
	private final Client client;
	private final String indexName;
	private final String typeName;
	private volatile ArrayList<Thread> threads;
	private volatile boolean closed = false;
	private final ArrayList<RssRiverFeedDefinition> feedsDefinition;
	private final List<StanbolAnalyser> analyzersList;
	private final List<MauiAnalyser> mauiAnalyserList;
	private final List<BayesianAnalyser> bayesianAnalyserList;
	
	
	@Inject
	public RssRiver(RiverName riverName, RiverSettings riverSettings,
			Client client) {
		super(riverName, riverSettings);
		this.client = client;
		
		Map<String,Object> settingsAsMap = riverSettings.settings();
		Map<String, Object> rssSettings = null;
		
		boolean isRiverDefined = settingsAsMap != null 
				&& settingsAsMap.containsKey("rss")
				&& (rssSettings = (Map<String, Object>) settingsAsMap.get("rss")) != null
				&& !rssSettings.isEmpty();
		
		if(!isRiverDefined) {
			this.logger.warn("There is no RSS river defined...exiting");
			System.exit(0);
		}
		
		boolean array = XContentMapValues.isArray(rssSettings.get("feeds"));
		if (array) {
			ArrayList<Map<String, Object>> feeds = (ArrayList<Map<String,Object>>) rssSettings
					.get("feeds");
			this.feedsDefinition = new ArrayList<RssRiverFeedDefinition>(feeds.size());
			for (Map<String,Object> feed : feeds) {
				String feedname = XContentMapValues.nodeStringValue(
						feed.get("name"), null);
				String url = XContentMapValues.nodeStringValue(
						feed.get("url"), null);
				int updateRate = XContentMapValues.nodeIntegerValue(
						feed.get("update_rate"), 900000);
				this.feedsDefinition.add(new RssRiverFeedDefinition(
						feedname, url, updateRate));
			}
		} else {
			String feedname = XContentMapValues.nodeStringValue(
					rssSettings.get("name"), null);
			String url = XContentMapValues.nodeStringValue(
					rssSettings.get("url"), null);
			
			int updateRate = XContentMapValues.nodeIntegerValue(
					rssSettings.get("update_rate"), 900000);

			this.feedsDefinition = new ArrayList<RssRiverFeedDefinition>(1);
			this.feedsDefinition.add(new RssRiverFeedDefinition(feedname, url,
					updateRate));
		}
		
		if (settingsAsMap.containsKey("index")) {
			Map<String,Object> indexSettings = (Map<String, Object>) settingsAsMap.get("index");

			this.indexName = XContentMapValues.nodeStringValue(
					indexSettings.get("index"), riverName.name());

			this.typeName = XContentMapValues.nodeStringValue(
					indexSettings.get("type"), "page");
		} else {
			this.indexName = riverName.name();
			this.typeName = "page";
		}
		
		boolean isAnalysersArray = XContentMapValues.isArray(rssSettings.get("stanbol_analysers"));
		if (isAnalysersArray) {
			ArrayList<Map<String, Object>> analyzers = (ArrayList<Map<String,Object>>) rssSettings
					.get("stanbol_analysers");
			this.analyzersList = new ArrayList<StanbolAnalyser>(analyzers.size());
			for (Map<String,Object> analyzer : analyzers) {
				String analyserName = XContentMapValues.nodeStringValue(
						analyzer.get("analyser_name"), null);
				String url = XContentMapValues.nodeStringValue(
						analyzer.get("url"), null);
				this.analyzersList.add(new StanbolAnalyser(analyserName, url));
			}
		}else {
			String analyserName = XContentMapValues.nodeStringValue(rssSettings.get("analyser_name"), null);
			String analyzerUrl = XContentMapValues.nodeStringValue(rssSettings.get("url"), "http://localhost:8080/enhancer");
			this.analyzersList = new ArrayList<StanbolAnalyser>(1);
			this.analyzersList.add(new StanbolAnalyser(analyserName, analyzerUrl));
		}
		
		boolean isMauiAnalysersArray = XContentMapValues.isArray(rssSettings.get("maui_analysers"));
		if (isMauiAnalysersArray) {
			ArrayList<Map<String, Object>> mauiAnalyzers = (ArrayList<Map<String,Object>>) rssSettings
					.get("maui_analysers");
			this.mauiAnalyserList = new ArrayList<MauiAnalyser>(mauiAnalyzers.size());
			for (Map<String,Object> ma : mauiAnalyzers) {
				String mauiVocabName = XContentMapValues.nodeStringValue(
						ma.get("maui_vocabulary_name"), null);
				String mauiModelName = XContentMapValues.nodeStringValue(
						ma.get("maui_model_name"), null);
				this.mauiAnalyserList.add(new MauiAnalyser(mauiVocabName, mauiModelName));
			}
		}else {
			String mauiVocabName = XContentMapValues.nodeStringValue(rssSettings.get("maui_vocabulary_name"), "agrovoc_en");
			String mauiModelName = XContentMapValues.nodeStringValue(rssSettings.get("maui_model_name"), "fa030");
			this.mauiAnalyserList = new ArrayList<MauiAnalyser>(1);
			this.mauiAnalyserList.add(new MauiAnalyser(mauiVocabName, mauiModelName));
		}
		
		boolean isBayesianAnalysersArray = XContentMapValues.isArray(rssSettings.get("bayesian_analysers"));
		if (isBayesianAnalysersArray) {
			ArrayList<Map<String, Object>> bayesianAnalyzers = (ArrayList<Map<String,Object>>) rssSettings
					.get("bayesian_analysers");
			this.bayesianAnalyserList = new ArrayList<BayesianAnalyser>(bayesianAnalyzers.size());
			for (Map<String,Object> ba : bayesianAnalyzers) {
				String analyserName = XContentMapValues.nodeStringValue(
						ba.get("analyser_name"), null);
				String analyserUrl = XContentMapValues.nodeStringValue(
						ba.get("analyser_url"), null);
				this.bayesianAnalyserList.add(new BayesianAnalyser(analyserName, analyserUrl));
			}
		}else {
			this.bayesianAnalyserList = new ArrayList<BayesianAnalyser>();
		}
			
	}

	public void start() {
		if (this.logger.isInfoEnabled())
			this.logger.info("Starting rss stream", new Object[0]);
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

		this.threads = new ArrayList(this.feedsDefinition.size());
		int threadNumber = 0;
		for (RssRiverFeedDefinition feedDefinition : this.feedsDefinition) {
			Thread thread = EsExecutors.daemonThreadFactory(
					this.settings.globalSettings(),
					"rss_slurper_" + threadNumber).newThread(
					new RSSParser(feedDefinition.getFeedname(), feedDefinition
							.getUrl(), feedDefinition.getUpdateRate()));

			thread.start();
			this.threads.add(thread);
			threadNumber++;
		}
	}

	public void close() {
		if (this.logger.isInfoEnabled())
			this.logger.info("Closing rss river", new Object[0]);
		this.closed = true;

		if (this.threads != null)
			for (Thread thread : this.threads)
				if (thread != null)
					thread.interrupt();
	}

	private SyndFeed getFeed(String url) {
		try {
			URL feedUrl = new URL(url);
			SyndFeedInput input = new SyndFeedInput();
			return input.build(new XmlReader(feedUrl));
		} catch (MalformedURLException e) {
			this.logger.error("RSS Url is incorrect : [{}].",e,
					new Object[] { url });
		} catch (IllegalArgumentException e) {
			this.logger.error("Feed from [{}] is incorrect.",e,
					new Object[] { url });
		} catch (FeedException e) {
			this.logger.error("Can not parse feed from [{}].",e,
					new Object[] { url });
		} catch (IOException e) {
			this.logger.error("Can not read feed from [{}].",e,
					new Object[] { url });
		}

		return null;
	}

	private class RSSParser implements Runnable {
		private String url;
		private int updateRate;
		private String feedname;

		public RSSParser(String feedname, String url, int updateRate) {
			if (RssRiver.this.logger.isInfoEnabled())
				RssRiver.this.logger
						.info("creating rss stream river [{}] for [{}] every [{}] ms",
								new Object[] { feedname, url,
										Integer.valueOf(updateRate) });
			this.feedname = feedname;
			this.url = url;
			this.updateRate = updateRate;
		}

		public void run() {
			while (true) {
				if (RssRiver.this.closed) {
					return;
				}

				SyndFeed feed = RssRiver.this.getFeed(this.url);
				if (feed != null) {
					Date feedDate = feed.getPublishedDate();
					if (RssRiver.this.logger.isInfoEnabled())
						RssRiver.this.logger.info("Feed publish date is {}",
								new Object[] { feedDate });

					String lastupdateField = "_lastupdated_"
							+ UUID.nameUUIDFromBytes(this.url.getBytes())
									.toString();
					Date lastDate = getLastDateFromRiver(lastupdateField);

					if ((lastDate == null)
							|| ((feedDate != null) && (feedDate.after(lastDate)))) {
						if (RssRiver.this.logger.isTraceEnabled())
							RssRiver.this.logger.trace("Feed is updated : {}",
									new Object[] { feed });

						BulkRequestBuilder bulk = RssRiver.this.client
								.prepareBulk();
						try {
							List<SyndEntry> entriesList = feed.getEntries();
							for (SyndEntry message : entriesList) {
								String description = "";
								if (message.getDescription() != null) {
									description = message.getDescription()
											.getValue();
								}

								String id = UUID.nameUUIDFromBytes(
										description.getBytes()).toString();

								GetResponse oldMessage = (GetResponse) RssRiver.this.client
										.prepareGet(RssRiver.this.indexName,
												RssRiver.this.typeName, id)
										.execute().actionGet();
								if (!oldMessage.isExists()) {
									if(RssRiver.this.logger.isInfoEnabled()) {
										RssRiver.this.logger
										.info("RSS feed received with title: {} and description: {}",
												new Object[] { message.getTitle(), description});
									}
									Map<String, List<EntityReference>> entities = getStanbolEntityFromRSSTitle(message.getTitle(), description);
									Map<String, List<String>> mauiTopics = getTopicsIndexedFromRSSTitle(message.getTitle(), description);
									List<Map<String,String>> bayesianAnalyzedText = getBayesianAnalyzedTextForRSS(message.getTitle(), description);
									
									populateFeed2BulkReques(bulk, message, id, entities, mauiTopics, bayesianAnalyzedText);
																		
									if (RssRiver.this.logger.isInfoEnabled())
										RssRiver.this.logger
												.info("FeedMessage update detected for source [{}]",
														new Object[] { this.feedname != null ? this.feedname
																: "undefined" });
									if (RssRiver.this.logger.isTraceEnabled())
										RssRiver.this.logger.trace(
												"FeedMessage is : {}",
												new Object[] { message });
								} else if (RssRiver.this.logger.isInfoEnabled()) {
									RssRiver.this.logger
											.info("FeedMessage {} already exist. Ignoring",
													new Object[] { id });
								}

							}

							if (RssRiver.this.logger.isDebugEnabled()) {
								RssRiver.this.logger
										.debug("processing [_seq  ]: [{}]/[{}]/[{}], last_seq [{}]",
												new Object[] {
														RssRiver.this.indexName,
														RssRiver.this.riverName
																.name(),
														lastupdateField,
														feedDate });
							}
							populateLastUpdate2BulkRequest(feedDate,
									lastupdateField, bulk);
						} catch (IOException e) {
							RssRiver.this.logger
									.warn("failed to add feed message entry to bulk indexing",
											new Object[0]);
						}
						try {
							BulkResponse response = (BulkResponse) bulk
									.execute().actionGet();
							if (response.hasFailures()) {
								RssRiver.this.logger.warn("failed to execute"
										+ response.buildFailureMessage(),
										new Object[0]);
							}
						} catch (Exception e) {
							RssRiver.this.logger.warn("failed to execute bulk",
									e, new Object[0]);
						}

					} else if (RssRiver.this.logger.isInfoEnabled()) {
						RssRiver.this.logger.info(
								"Nothing new in the feed... Relaxing...",
								new Object[0]);
					}
				}
				try {
					if (RssRiver.this.logger.isInfoEnabled())
						RssRiver.this.logger
								.info("Rss river is going to sleep for {} ms",
										new Object[] { Integer
												.valueOf(this.updateRate) });
					Thread.sleep(this.updateRate);
				} catch (InterruptedException e1) {
				}
			}
		}

		private void populateLastUpdate2BulkRequest(Date feedDate,
				String lastupdateField, BulkRequestBuilder bulk)
				throws IOException {
			bulk.add(Requests
					.indexRequest("_river")
					.type(RssRiver.this.riverName.name())
					.id(lastupdateField)
					.source(XContentFactory.jsonBuilder()
							.startObject().startObject("rss")
							.field(lastupdateField, feedDate)
							.endObject().endObject()));
		}

		private void populateFeed2BulkReques(BulkRequestBuilder bulk,
				SyndEntry message, String id, Map<String, List<EntityReference>> entities,
				Map<String,List<String>> mauiTopics, List<Map<String,String>> bayesianAnalyzedText) throws IOException {
			
			if(entities == null || entities.isEmpty()) {
				if(RssRiver.this.logger.isWarnEnabled()) {
					RssRiver.this.logger.warn("There is no Stanbol content enhancement done for entity Linking...indexing RSS feed");
				}
			} 
			
			if(mauiTopics == null || mauiTopics.isEmpty()) {
				if(RssRiver.this.logger.isWarnEnabled()) {
					RssRiver.this.logger.warn("There is no MAUI topics indexed...indexing RSS feed");
				}
			}
			
			if(bayesianAnalyzedText == null || bayesianAnalyzedText.isEmpty()) {
				if(RssRiver.this.logger.isWarnEnabled()) {
					RssRiver.this.logger.warn("There is no Bayesian analyzed text indexed...indexing RSS feed");
				}
			}
			
			XContentBuilder cb = RssToJson.toJson(
					message,
					RssRiver.this.riverName.getName(),
					this.feedname, 
					entities, mauiTopics, bayesianAnalyzedText);
			
			if(RssRiver.this.logger.isDebugEnabled()) {
				RssRiver.this.logger.debug("RSS Data to be indexed post Stanbol,MAUI and Bayesian analysis: {}", cb.string());
			}
						
			bulk.add(Requests
					.indexRequest(
							RssRiver.this.indexName)
					.type(RssRiver.this.typeName)
					.id(id)
					.source(cb));
		}

		private Date getLastDateFromRiver(String lastupdateField) {
			Date lastDate = null;
			try {
				if (RssRiver.this.logger.isDebugEnabled())
					RssRiver.this.logger.debug("Starting to parse RSS feed",
							new Object[0]);
				RssRiver.this.client.admin().indices()
						.prepareRefresh(new String[] { "_river" }).execute()
						.actionGet();
				GetResponse lastSeqGetResponse = (GetResponse) RssRiver.this.client
						.prepareGet("_river", RssRiver.this.riverName().name(),
								lastupdateField).execute().actionGet();

				if (lastSeqGetResponse.isExists()) {
					Map rssState = (Map) lastSeqGetResponse.getSourceAsMap().get(
							"rss");

					if (rssState != null) {
						Object lastupdate = rssState.get(lastupdateField);
						if (lastupdate != null) {
							String strLastDate = lastupdate.toString();
							lastDate = ISODateTimeFormat
									.dateOptionalTimeParser()
									.parseDateTime(strLastDate).toDate();
						}
					}

				} else if (RssRiver.this.logger.isDebugEnabled()) {
					RssRiver.this.logger.debug("{} doesn't exist",
							new Object[] { lastupdateField });
				}
			} catch (Exception e) {
				RssRiver.this.logger.warn(
						"failed to get _lastupdate, throttling....", e,
						new Object[0]);
			}
			return lastDate;
		}
		
		private Map<String, List<EntityReference>> getStanbolEntityFromRSSTitle(String title, String description) {
			if (RssRiver.this.logger.isInfoEnabled())
				RssRiver.this.logger.info("ES-Stanbol communication starting...");
			EsStanbolRestCommunicator stanbolCommunicator = new EsStanbolRestCommunicator(RssRiver.this.logger, RssRiver.this.analyzersList);
			try {
				return stanbolCommunicator.getEntity(title);
			} catch (IOException e) {
				return null;
			}
		}
		
		private Map<String,List<String>> getTopicsIndexedFromRSSTitle(String title, String description) {
			if (RssRiver.this.logger.isInfoEnabled())
				RssRiver.this.logger.info("MAUI topic index requesting...");
			Map<String, List<String>> mauiTopicsByVocabulary = new HashMap<String, List<String>>();
			for(MauiAnalyser ma : RssRiver.this.mauiAnalyserList) {
				String key = ma.getVocabulary()+"-"+ma.getModel();
				List<String> topicsList = MauiWrapper.extractTopicsForText(ma.getVocabulary(),  ma.getModel(), title); //, RssRiver.this.logger);
				mauiTopicsByVocabulary.put(key, topicsList);
			}
			return mauiTopicsByVocabulary;
			
		}
		
		private List<Map<String, String>> getBayesianAnalyzedTextForRSS(String title, String description) {
			if(RssRiver.this.bayesianAnalyserList.isEmpty()) {
				if (RssRiver.this.logger.isInfoEnabled())
					RssRiver.this.logger.info("There is no Bayesian analyser defined .");
				return null;
			}
			
			if (RssRiver.this.logger.isInfoEnabled())
				RssRiver.this.logger.info("Bayesian analyser starting...");
			
			ESBayesianRestCommunicator.logger = RssRiver.this.logger;
			List<Map<String, String>> bayesianAnalyzedDataList = new ArrayList<Map<String, String>>();
			for(BayesianAnalyser ba : RssRiver.this.bayesianAnalyserList) {
				bayesianAnalyzedDataList.add(ESBayesianRestCommunicator.getRSSResponse(ba.getAnalyserUrl(), title,  description));
			}
			return bayesianAnalyzedDataList;
			
		}
		
	} //End of RSSParser
	
	
}