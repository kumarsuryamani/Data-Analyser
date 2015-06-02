package org.elasticsearch.river.facebook;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.river.River;

public class FacebookRiverModule extends AbstractModule
{
  protected void configure()
  {
    bind(River.class).to(FacebookRiver.class).asEagerSingleton();
  }
}