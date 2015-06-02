package org.elasticsearch.river.rss;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.river.River;

public class RssRiverModule extends AbstractModule
{
  protected void configure()
  {
    bind(River.class).to(RssRiver.class).asEagerSingleton();
  }
}