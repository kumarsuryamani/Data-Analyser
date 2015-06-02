/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.river.twitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import maui.main.MauiWrapper;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.elasticsearch.threadpool.ThreadPool;

import twitter4j.FilterQuery;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.StatusDeletionNotice;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.URLEntity;
import twitter4j.UserMentionEntity;
import twitter4j.conf.ConfigurationBuilder;
import es.bayesian.link.BayesianAnalyser;
import es.bayesian.link.ESBayesianRestCommunicator;
import es.maui.link.MauiAnalyser;
import es.stanbol.link.EntityReference;
import es.stanbol.link.EsStanbolRestCommunicator;
import es.stanbol.link.StanbolAnalyser;

/**
 *
 */
public class TwitterRiver extends AbstractRiverComponent implements River {

    private final ThreadPool threadPool;

    private final Client client;

    //private String user;
    //private String password;

    private String oauthConsumerKey = null;
    private String oauthConsumerSecret = null;
    private String oauthAccessToken = null;
    private String oauthAccessTokenSecret = null;

    private String proxyHost;
    private String proxyPort;
    private String proxyUser;
    private String proxyPassword;

    private final String indexName;

    private final String typeName;

    private final int bulkSize;

    private final int dropThreshold;

    private FilterQuery filterQuery;

    private String streamType;


    private volatile TwitterStream stream;

    private final AtomicInteger onGoingBulks = new AtomicInteger();

    private volatile BulkRequestBuilder currentRequest;

    private volatile boolean closed = false;
    
    private final List<StanbolAnalyser> analyzersList;
    
    private final List<MauiAnalyser> mauiAnalyserList;
    private final List<BayesianAnalyser> bayesianAnalyserList;

    @SuppressWarnings({"unchecked"})
    @Inject
    public TwitterRiver(RiverName riverName, RiverSettings settings, Client client, ThreadPool threadPool) {
        super(riverName, settings);
        this.client = client;
        this.threadPool = threadPool;
        
        if(!settings.settings().containsKey("twitter")) {
			this.logger.warn("There is no Twitter river defined...exiting");
			System.exit(0);
		}

        Map<String, Object> twitterSettings = (Map<String, Object>) settings.settings().get("twitter");
        //user = XContentMapValues.nodeStringValue(twitterSettings.get("user"), null);
        //password = XContentMapValues.nodeStringValue(twitterSettings.get("password"), null);
        if (twitterSettings.containsKey("oauth")) {
            Map<String, Object> oauth = (Map<String, Object>) twitterSettings.get("oauth");
            if (oauth.containsKey("consumerKey")) {
                oauthConsumerKey = XContentMapValues.nodeStringValue(oauth.get("consumerKey"), null);
            }
            if (oauth.containsKey("consumer_key")) {
                oauthConsumerKey = XContentMapValues.nodeStringValue(oauth.get("consumer_key"), null);
            }
            if (oauth.containsKey("consumerSecret")) {
                oauthConsumerSecret = XContentMapValues.nodeStringValue(oauth.get("consumerSecret"), null);
            }
            if (oauth.containsKey("consumer_secret")) {
                oauthConsumerSecret = XContentMapValues.nodeStringValue(oauth.get("consumer_secret"), null);
            }
            if (oauth.containsKey("accessToken")) {
                oauthAccessToken = XContentMapValues.nodeStringValue(oauth.get("accessToken"), null);
            }
            if (oauth.containsKey("access_token")) {
                oauthAccessToken = XContentMapValues.nodeStringValue(oauth.get("access_token"), null);
            }
            if (oauth.containsKey("accessTokenSecret")) {
                oauthAccessTokenSecret = XContentMapValues.nodeStringValue(oauth.get("accessTokenSecret"), null);
            }
            if (oauth.containsKey("access_token_secret")) {
                oauthAccessTokenSecret = XContentMapValues.nodeStringValue(oauth.get("access_token_secret"), null);
            }
        }
        if (twitterSettings.containsKey("proxy")) {
            Map<String, Object> proxy = (Map<String, Object>) twitterSettings.get("proxy");
            if (proxy.containsKey("host")) {
                proxyHost = XContentMapValues.nodeStringValue(proxy.get("host"), null);
            }
            if (proxy.containsKey("port")) {
                proxyPort = XContentMapValues.nodeStringValue(proxy.get("port"), null);
            }
            if (proxy.containsKey("user")) {
                proxyUser = XContentMapValues.nodeStringValue(proxy.get("user"), null);
            }
            if (proxy.containsKey("password")) {
                proxyPassword = XContentMapValues.nodeStringValue(proxy.get("password"), null);
            }
        }
        streamType = XContentMapValues.nodeStringValue(twitterSettings.get("type"), "sample");
        Map<String, Object> filterSettings = (Map<String, Object>) twitterSettings.get("filter");
        if (filterSettings != null) {
            filterQuery = new FilterQuery();
            filterQuery.count(XContentMapValues.nodeIntegerValue(filterSettings.get("count"), 0));
            Object tracks = filterSettings.get("tracks");
            if (tracks != null) {
                if (tracks instanceof List) {
                    List<String> lTracks = (List<String>) tracks;
                    filterQuery.track(lTracks.toArray(new String[lTracks.size()]));
                } else {
                    filterQuery.track(Strings.commaDelimitedListToStringArray(tracks.toString()));
                }
            }
            Object follow = filterSettings.get("follow");
            if (follow != null) {
                if (follow instanceof List) {
                    List lFollow = (List) follow;
                    long[] followIds = new long[lFollow.size()];
                    for (int i = 0; i < lFollow.size(); i++) {
                        Object o = lFollow.get(i);
                        if (o instanceof Number) {
                            followIds[i] = ((Number) o).intValue();
                        } else {
                            followIds[i] = Integer.parseInt(o.toString());
                        }
                    }
                    filterQuery.follow(followIds);
                } else {
                    String[] ids = Strings.commaDelimitedListToStringArray(follow.toString());
                    long[] followIds = new long[ids.length];
                    for (int i = 0; i < ids.length; i++) {
                        followIds[i] = Integer.parseInt(ids[i]);
                    }
                    filterQuery.follow(followIds);
                }
            }
            Object locations = filterSettings.get("locations");
            if (locations != null) {
                if (locations instanceof List) {
                    List lLocations = (List) locations;
                    double[][] dLocations = new double[lLocations.size()][];
                    for (int i = 0; i < lLocations.size(); i++) {
                        Object loc = lLocations.get(i);
                        double lat;
                        double lon;
                        if (loc instanceof List) {
                            List lLoc = (List) loc;
                            if (lLoc.get(0) instanceof Number) {
                                lat = ((Number) lLoc.get(0)).doubleValue();
                            } else {
                                lat = Double.parseDouble(lLoc.get(0).toString());
                            }
                            if (lLoc.get(1) instanceof Number) {
                                lon = ((Number) lLoc.get(1)).doubleValue();
                            } else {
                                lon = Double.parseDouble(lLoc.get(1).toString());
                            }
                        } else {
                            String[] sLoc = Strings.commaDelimitedListToStringArray(loc.toString());
                            lat = Double.parseDouble(sLoc[0]);
                            lon = Double.parseDouble(sLoc[1]);
                        }
                        dLocations[i] = new double[]{lat, lon};
                    }
                    filterQuery.locations(dLocations);
                } else {
                    String[] sLocations = Strings.commaDelimitedListToStringArray(locations.toString());
                    double[][] dLocations = new double[sLocations.length / 2][];
                    int dCounter = 0;
                    for (int i = 0; i < sLocations.length; i++) {
                        double lat = Double.parseDouble(sLocations[i]);
                        double lon = Double.parseDouble(sLocations[++i]);
                        dLocations[dCounter++] = new double[]{lat, lon};
                    }
                    filterQuery.locations(dLocations);
                }
            }
        }
        
        //Stanbol Analysing
        boolean isAnalysersArray = XContentMapValues.isArray(twitterSettings.get("stanbol_analysers"));
		if (isAnalysersArray) {
			ArrayList<Map<String, Object>> analyzers = (ArrayList<Map<String,Object>>) twitterSettings
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
			String analyserName = XContentMapValues.nodeStringValue(twitterSettings.get("analyser_name"), null);
			String analyzerUrl = XContentMapValues.nodeStringValue(twitterSettings.get("url"), "http://localhost:8080/enhancer");
			this.analyzersList = new ArrayList<StanbolAnalyser>(1);
			this.analyzersList.add(new StanbolAnalyser(analyserName, analyzerUrl));
		}
		
		//MAUI Analysing		
		boolean isMauiAnalysersArray = XContentMapValues.isArray(twitterSettings.get("maui_analysers"));
		if (isMauiAnalysersArray) {
			ArrayList<Map<String, Object>> mauiAnalyzers = (ArrayList<Map<String,Object>>) twitterSettings
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
			String mauiVocabName = XContentMapValues.nodeStringValue(twitterSettings.get("maui_vocabulary_name"), "agrovoc_en");
			String mauiModelName = XContentMapValues.nodeStringValue(twitterSettings.get("maui_model_name"), "fa030");
			this.mauiAnalyserList = new ArrayList<MauiAnalyser>(1);
			this.mauiAnalyserList.add(new MauiAnalyser(mauiVocabName, mauiModelName));
		}
       
		//BayesianAnalyzing
		boolean isBayesianAnalysersArray = XContentMapValues.isArray(twitterSettings.get("bayesian_analysers"));
		if (isBayesianAnalysersArray) {
			ArrayList<Map<String, Object>> bayesianAnalyzers = (ArrayList<Map<String,Object>>) twitterSettings
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
		
        //logger.info("creating twitter stream river for [{}]", user);

        if (/*user == null && password == null && */oauthAccessToken == null && oauthConsumerKey == null && oauthConsumerSecret == null && oauthAccessTokenSecret == null) {
            stream = null;
            indexName = null;
            typeName = "status";
            bulkSize = 100;
            dropThreshold = 10;
            logger.warn("no user/password or oauth specified, disabling river...");
            return;
        }

        if (settings.settings().containsKey("index")) {
            Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
            indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
            typeName = XContentMapValues.nodeStringValue(indexSettings.get("type"), "status");
            this.bulkSize = XContentMapValues.nodeIntegerValue(indexSettings.get("bulk_size"), 100);
            this.dropThreshold = XContentMapValues.nodeIntegerValue(indexSettings.get("drop_threshold"), 10);
        } else {
            indexName = riverName.name();
            typeName = "status";
            bulkSize = 100;
            dropThreshold = 10;
        }
        
        ConfigurationBuilder cb = new ConfigurationBuilder();
        if (oauthAccessToken != null && oauthConsumerKey != null && oauthConsumerSecret != null && oauthAccessTokenSecret != null) {
            cb.setOAuthConsumerKey(oauthConsumerKey)
                    .setOAuthConsumerSecret(oauthConsumerSecret)
                    .setOAuthAccessToken(oauthAccessToken)
                    .setOAuthAccessTokenSecret(oauthAccessTokenSecret);
        } /*else {
            cb.setUser(user).setPassword(password);
        }*/
        if (proxyHost != null) cb.setHttpProxyHost(proxyHost);
        if (proxyPort != null) cb.setHttpProxyPort(Integer.parseInt(proxyPort));
        if (proxyUser != null) cb.setHttpProxyUser(proxyUser);
        if (proxyPassword != null) cb.setHttpProxyHost(proxyPassword);
        stream = new TwitterStreamFactory(cb.build()).getInstance();
        stream.addListener(new StatusHandler());
    }

    @Override
    public void start() {
        if (stream == null) {
            return;
        }
        logger.info("starting twitter stream");
        try {
            String mapping = XContentFactory.jsonBuilder().startObject().startObject(typeName).startObject("properties")
                    .startObject("location").field("type", "geo_point").endObject()
                    .startObject("user").startObject("properties").startObject("screen_name").field("type", "string").field("index", "not_analyzed").endObject().endObject().endObject()
                    .startObject("mention").startObject("properties").startObject("screen_name").field("type", "string").field("index", "not_analyzed").endObject().endObject().endObject()
                    .startObject("in_reply").startObject("properties").startObject("user_screen_name").field("type", "string").field("index", "not_analyzed").endObject().endObject().endObject()
                    .endObject().endObject().endObject().string();
            client.admin().indices().prepareCreate(indexName).addMapping(typeName, mapping).execute().actionGet();
        } catch (Exception e) {
            if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
                // that's fine
            } else if (ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException) {
                // ok, not recovered yet..., lets start indexing and hope we recover by the first bulk
                // TODO: a smarter logic can be to register for cluster event listener here, and only start sampling when the block is removed...
            } else {
                logger.warn("failed to create index [{}], disabling river...", e, indexName);
                return;
            }
        }
        currentRequest = client.prepareBulk();
        if (streamType.equals("filter") || filterQuery != null) {

            stream.filter(filterQuery);

        } else if (streamType.equals("firehose")) {
            stream.firehose(0);
        } else {
            stream.sample();
        }
    }

    private void reconnect() {
        if (closed) {
            return;
        }
        try {
            stream.cleanUp();
        } catch (Exception e) {
            logger.debug("failed to cleanup after failure", e);
        }
        try {
            stream.shutdown();
        } catch (Exception e) {
            logger.debug("failed to shutdown after failure", e);
        }
        if (closed) {
            return;
        }

        try {
            ConfigurationBuilder cb = new ConfigurationBuilder();
            if (oauthAccessToken != null && oauthConsumerKey != null && oauthConsumerSecret != null && oauthAccessTokenSecret != null) {
                cb.setOAuthConsumerKey(oauthConsumerKey)
                        .setOAuthConsumerSecret(oauthConsumerSecret)
                        .setOAuthAccessToken(oauthAccessToken)
                        .setOAuthAccessTokenSecret(oauthAccessTokenSecret);
            } /*else {
                cb.setUser(user).setPassword(password);
            }*/
            if (proxyHost != null) cb.setHttpProxyHost(proxyHost);
            if (proxyPort != null) cb.setHttpProxyPort(Integer.parseInt(proxyPort));
            if (proxyUser != null) cb.setHttpProxyUser(proxyUser);
            if (proxyPassword != null) cb.setHttpProxyHost(proxyPassword);
            stream = new TwitterStreamFactory(cb.build()).getInstance();
            stream.addListener(new StatusHandler());

            if (streamType.equals("filter") || filterQuery != null) {
                stream.filter(filterQuery);

            } else if (streamType.equals("firehose")) {
                stream.firehose(0);
            } else {
                stream.sample();
            }
        } catch (Exception e) {
            if (closed) {
                close();
                return;
            }
            // TODO, we can update the status of the river to RECONNECT
            logger.warn("failed to connect after failure, throttling", e);
            threadPool.schedule(TimeValue.timeValueMinutes(10), ThreadPool.Names.GET, new Runnable() {
                @Override
                public void run() {
                    reconnect();
                }
            });
        }
    }

    @Override
    public void close() {
        this.closed = true;
        logger.info("closing twitter stream river");
        if (stream != null) {
            stream.cleanUp();
            stream.shutdown();
        }
    }

    private class StatusHandler extends StatusAdapter {

        @Override
        public void onStatus(Status status) {
            if (logger.isTraceEnabled()) {
                logger.trace("status {} : {}", status.getUser().getName(), status.getText());
            }
            try {
                XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
                builder.field("text", status.getText());
                builder.field("created_at", status.getCreatedAt());
                builder.field("source", status.getSource());
                builder.field("truncated", status.isTruncated());

                if (status.getUserMentionEntities() != null) {
                    builder.startArray("mention");
                    for (UserMentionEntity user : status.getUserMentionEntities()) {
                        builder.startObject();
                        builder.field("id", user.getId());
                        builder.field("name", user.getName());
                        builder.field("screen_name", user.getScreenName());
                        builder.field("start", user.getStart());
                        builder.field("end", user.getEnd());
                        builder.endObject();
                    }
                    builder.endArray();
                }

                if (status.getRetweetCount() != -1) {
                    builder.field("retweet_count", status.getRetweetCount());
                }

                if (status.getInReplyToStatusId() != -1) {
                    builder.startObject("in_reply");
                    builder.field("status", status.getInReplyToStatusId());
                    if (status.getInReplyToUserId() != -1) {
                        builder.field("user_id", status.getInReplyToUserId());
                        builder.field("user_screen_name", status.getInReplyToScreenName());
                    }
                    builder.endObject();
                }

                if (status.getHashtagEntities() != null) {
                    builder.startArray("hashtag");
                    for (HashtagEntity hashtag : status.getHashtagEntities()) {
                        builder.startObject();
                        builder.field("text", hashtag.getText());
                        builder.field("start", hashtag.getStart());
                        builder.field("end", hashtag.getEnd());
                        builder.endObject();
                    }
                    builder.endArray();
                }
               /* if (status.getContributors() != null) {
                    builder.array("contributor", status.getContributors());
                }*/
                if (status.getGeoLocation() != null) {
                    builder.startObject("location");
                    builder.field("lat", status.getGeoLocation().getLatitude());
                    builder.field("lon", status.getGeoLocation().getLongitude());
                    builder.endObject();
                }
                if (status.getPlace() != null) {
                    builder.startObject("place");
                    builder.field("id", status.getPlace().getId());
                    builder.field("name", status.getPlace().getName());
                    builder.field("type", status.getPlace().getPlaceType());
                    builder.field("full_name", status.getPlace().getFullName());
                    builder.field("street_address", status.getPlace().getStreetAddress());
                    builder.field("country", status.getPlace().getCountry());
                    builder.field("country_code", status.getPlace().getCountryCode());
                    builder.field("url", status.getPlace().getURL());
                    builder.endObject();
                }
                if (status.getURLEntities() != null) {
                    builder.startArray("link");
                    for (URLEntity url : status.getURLEntities()) {
                        if (url != null) {
                            builder.startObject();
                            if (url.getURL() != null) {
                                builder.field("url", url.getURL());
                            }
                            if (url.getDisplayURL() != null) {
                                builder.field("display_url", url.getDisplayURL());
                            }
                            if (url.getExpandedURL() != null) {
                                builder.field("expand_url", url.getExpandedURL());
                            }
                            builder.field("start", url.getStart());
                            builder.field("end", url.getEnd());
                            builder.endObject();
                        }
                    }
                    builder.endArray();
                }
                /*if (status..getAnnotations() != null) {
                    builder.startObject("annotation");
                    List<Annotation> annotations = status.getAnnotations().getAnnotations();
                    for (Annotation ann : annotations) {
                        builder.startObject(ann.getType());
                        Map<String, String> attributes = ann.getAttributes();
                        for (Map.Entry<String, String> entry : attributes.entrySet()) {
                            builder.field(entry.getKey(), entry.getValue());
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }*/

                builder.startObject("user");
                builder.field("id", status.getUser().getId());
                builder.field("name", status.getUser().getName());
                builder.field("screen_name", status.getUser().getScreenName());
                builder.field("location", status.getUser().getLocation());
                builder.field("description", status.getUser().getDescription());
                builder.endObject();
                
                Map<String, List<EntityReference>> entities = getStanbolEntityForTwitterUserStatus(status.getUser().getName(), status.getText());
				if(entities != null) {
					if(entities.isEmpty()) {
						if(logger.isWarnEnabled()) {
							logger.warn("There is no Stanbol content enhancement done for entity Linking...indexing entities as empty text");
						}
						builder.field("entities", "{}");
					}else {
						builder.startArray("entities");    
	            	    Map<String,Object> analyserMap = new HashMap<String, Object>();
	            	    for(Map.Entry<String, List<EntityReference>> entityEntry : entities.entrySet()) {
	            	    	List<Map<String,Object>> entityMapList = new ArrayList<Map<String,Object>>();
	            	    	for(EntityReference er : entityEntry.getValue()) {
	            	        	entityMapList.add(getEntityMap(er));
	            	        }
	            	    	analyserMap.put(entityEntry.getKey(), entityMapList);
	            	    }
	            	    builder.value(analyserMap);
	            	    builder.endArray();
					}
				}
				Map<String, List<String>> mauiTopics = getTopicsIndexedForTwitterUserStatus(status.getUser().getName(), status.getText());
				if(mauiTopics == null || mauiTopics.isEmpty()) {
					if(logger.isWarnEnabled()) {
						logger.warn("There is no MAUI topics indexed...indexing MAUI topics as empty text");
					}
				}
				
				if(mauiTopics != null) {
			    	if(mauiTopics.isEmpty()) {
			    		builder.field("topics", "{}");
			    	}else {
			    		builder.startArray("topics");
			    		builder.startObject();
			    		for(Map.Entry<String, List<String>> e : mauiTopics.entrySet()) {
			    			builder.startArray(e.getKey());
			    			for(String s : e.getValue()) {
			    				builder.startObject();
			    				builder.field("text", s);
			    				builder.endObject();
			    			}
			    			builder.endArray();
			    		}
			    		builder.endObject();
			    		builder.endArray();
			    	}
			    }
				
				List<Map<String,String>> bayesianAnalyzedTextList = getBayesianAnalyzedTextForTweet(status.getText());
				if(bayesianAnalyzedTextList == null || bayesianAnalyzedTextList.isEmpty()) {
					if(logger.isWarnEnabled()) {
						logger.warn("There is no Bayesian analyzed text indexed...indexing Bayesian Analyzers as empty text");
					}
				}
				
				if(bayesianAnalyzedTextList != null) {
			    	if(bayesianAnalyzedTextList.isEmpty()) {
			    		builder.field("BayesianAnalysers", "{}");
			    	} else{
			    		builder.startArray("BayesianAnalysers");
			    		builder.startObject();
			    		for(Map<String, String> m : bayesianAnalyzedTextList) {
			    			for(Map.Entry<String, String> e : m.entrySet()) {
			    				builder.startArray(e.getKey());
			    				builder.startObject();
			    				builder.field("text", e.getValue());
			    				builder.endObject();
			    				builder.endArray();
			    			}
			    		}
			    		builder.endObject();
			    		builder.endArray();
			    	}
				}
				
				builder.endObject();
                currentRequest.add(Requests.indexRequest(indexName).type(typeName).id(Long.toString(status.getId())).create(true).source(builder));
                processBulkIfNeeded();
            } catch (Exception e) {
                logger.warn("failed to construct index request", e);
            }
        }

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            if (statusDeletionNotice.getStatusId() != -1) {
                currentRequest.add(Requests.deleteRequest(indexName).type(typeName).id(Long.toString(statusDeletionNotice.getStatusId())));
                processBulkIfNeeded();
            }
        }

        @Override
        public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
            logger.info("received track limitation notice, number_of_limited_statuses {}", numberOfLimitedStatuses);
        }

        @Override
        public void onException(Exception ex) {
            logger.warn("stream failure, restarting stream...", ex);
            threadPool.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    reconnect();
                }
            }, TimeValue.timeValueMinutes(10));
        }

        private void processBulkIfNeeded() {
            if (currentRequest.numberOfActions() >= bulkSize) {
                // execute the bulk operation
                int currentOnGoingBulks = onGoingBulks.incrementAndGet();
                if (currentOnGoingBulks > dropThreshold) {
                    onGoingBulks.decrementAndGet();
                    logger.warn("dropping bulk, [{}] crossed threshold [{}]", onGoingBulks, dropThreshold);
                } else {
                    try {
                        currentRequest.execute(new ActionListener<BulkResponse>() {
                            @Override
                            public void onResponse(BulkResponse bulkResponse) {
                                onGoingBulks.decrementAndGet();
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                onGoingBulks.decrementAndGet();
                                logger.warn("failed to execute bulk");
                            }
                        });
                    } catch (Exception e) {
                        onGoingBulks.decrementAndGet();
                        logger.warn("failed to process bulk", e);
                    }
                }
                currentRequest = client.prepareBulk();
            }
        }
    
        private Map<String,Object> getEntityMap(EntityReference er) {
      	  Map<String, Object> entityMap = new HashMap<String, Object>();
      	  entityMap.put("entity-label",er.getEntityLabel());
      	  entityMap.put("entity-reference", er.getEntityReference());
      	  entityMap.put("entity-confidence", er.getConfidence());
      	  entityMap.put("entity-type", getEntityTypeMap(er.getEntityTypeList()));
      	  return entityMap;
        }
        
        private Map<String,String> getEntityTypeMap(List<String> list) {
      	  Map<String, String> entityTypeMap = new HashMap<String, String>();
      	  
      	  int count = 0;
      	  for(String s : list) {
      		entityTypeMap.put("entity-type-"+(count++), s);  
      	  }
      	  return entityTypeMap;
        }
        
        private Map<String, List<EntityReference>> getStanbolEntityForTwitterUserStatus(String userName, String statusText) {
			if (logger.isInfoEnabled())
				logger.info("ES-Stanbol communication starting...");
			EsStanbolRestCommunicator stanbolCommunicator = new EsStanbolRestCommunicator(logger, analyzersList);
			try {
				return stanbolCommunicator.getEntity(userName);
			} catch (IOException e) {
				return null;
			}
		}
        
        private Map<String,List<String>> getTopicsIndexedForTwitterUserStatus(String userName, String statusText) {
			if (logger.isInfoEnabled())
					logger.info("MAUI topic index requesting...");
			Map<String, List<String>> mauiTopicsByVocabulary = new HashMap<String, List<String>>();
			String textForAnalysed = userName+" "+statusText;
			for(MauiAnalyser ma : mauiAnalyserList) {
				String key = ma.getVocabulary()+"-"+ma.getModel();
				List<String> topicsList = MauiWrapper.extractTopicsForText(ma.getVocabulary(),  ma.getModel(), textForAnalysed);
				mauiTopicsByVocabulary.put(key, topicsList);
			}
			return mauiTopicsByVocabulary;
			
		}
        
        private List<Map<String,String>> getBayesianAnalyzedTextForTweet(String statusText) {
        	if(bayesianAnalyserList.isEmpty()) {
				if (logger.isInfoEnabled())
					logger.info("There is no Bayesian analyser defined .");
				return null;
			}
			if (logger.isInfoEnabled())
				logger.info("Bayesian analyser starting...");
			ESBayesianRestCommunicator.logger = logger;
			List<Map<String, String>> bayesianAnalyzedDataList = new ArrayList<Map<String, String>>();
			for(BayesianAnalyser ba : bayesianAnalyserList) {
				Map<String, String> bayesianAnalyzedData = ESBayesianRestCommunicator.getTweetResponse(ba.getAnalyserUrl(), statusText);
				if(bayesianAnalyzedData != null && bayesianAnalyzedData.size() > 0) {
					bayesianAnalyzedDataList.add(bayesianAnalyzedData);
				}
			}
			return bayesianAnalyzedDataList;
			
		}
    
    }
}