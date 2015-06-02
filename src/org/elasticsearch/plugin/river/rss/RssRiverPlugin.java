package org.elasticsearch.plugin.river.rss;

import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.river.RiversModule;
import org.elasticsearch.river.rss.RssRiverModule;

public class RssRiverPlugin extends AbstractPlugin
{
  public String name() {
    return "river-rss";
  }

  public String description() {
    return "River Rss Plugin";
  }

  public void processModule(Module module) {
    if ((module instanceof RiversModule))
      ((RiversModule)module).registerRiver("rss", RssRiverModule.class);
  }
}