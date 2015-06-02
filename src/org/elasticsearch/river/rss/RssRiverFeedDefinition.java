package org.elasticsearch.river.rss;

public class RssRiverFeedDefinition
{
  private String feedname;
  private String url;
  private int updateRate;

  public RssRiverFeedDefinition()
  {
  }

  public RssRiverFeedDefinition(String feedname, String url, int updateRate)
  {
    this.feedname = feedname;
    this.url = url;
    this.updateRate = updateRate;
  }

  public String getFeedname() {
    return this.feedname;
  }

  public void setFeedname(String feedname) {
    this.feedname = feedname;
  }

  public String getUrl() {
    return this.url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getUpdateRate() {
    return this.updateRate;
  }

  public void setUpdateRate(int updateRate) {
    this.updateRate = updateRate;
  }
}
