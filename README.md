# ksm-opensource-projects

This project provides feature of data searching and analysis from Facebook, Twitter and configurable RSS feeds sources. The searched data is analysed with semantic content management services like Apache Stanbol (https://stanbol.apache.org),  MAUI(https://code.google.com/p/maui-indexer/) and Baysesian analysis services. The technical implementation uses Elasticsearch technologies (v0.90) and specially River plugin concept. More about the River concept can be read here:
https://www.elastic.co/guide/en/elasticsearch/reference/0.90/modules-plugins.html#river

The Twitter and RSS River plugins are basically obtained from https://github.com/elastic/elasticsearch-river-twitter and http://david.pilato.fr/rssriver/ respectively. The base version is extended and customized to add support for content analysis.

Project includes source fro MAUI topic indexing from https://code.google.com/p/maui-indexer/ .
Project has sufficient request examples included for the end user to go ahead and start.


