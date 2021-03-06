Universal remote system indexing River Plugin for ElasticSearch
===============================================================

The Universal remote system indexing River Plugin allows index documents from
remotely accessible systems into [ElasticSearch](http://www.elasticsearch.org). 
It's implemented as ElasticSearch [river](http://www.elasticsearch.org/guide/reference/river/) 
[plugin](http://www.elasticsearch.org/guide/reference/modules/plugins.html) 
and uses remote APIs (REST with JSON or XML, SOAP etc.) to obtain documents 
from remote systems.

In order to install the plugin into ElasticSearch, simply run: 
`bin/plugin -url https://repository.jboss.org/nexus/content/groups/public-jboss/org/jboss/elasticsearch/elasticsearch-river-remote/1.1.0/elasticsearch-river-remote-1.1.0.zip -install elasticsearch-river-remote`.

    --------------------------------------------------
    | Remote River | ElasticSearch    | Release date |
    --------------------------------------------------
    | master       | 0.90.0           |              |
    --------------------------------------------------
    | 1.1.0        | 0.90.0           | 16.5.2013    |
    --------------------------------------------------
    | 1.0.0        | 0.19.12          | 14.5.2013    |
    --------------------------------------------------

For changelog, planned milestones/enhancements and known bugs see 
[github issue tracker](https://github.com/jbossorg/elasticsearch-river-remote/issues) please.

The river indexes documents with comments from remote system, and makes them searchable
by ElasticSearch. Remote system is pooled periodically to detect changed documents and 
update search index in incremental update mode. 
Periodical full update may be configured too to completely refresh search index and 
remove documents deleted in remote system (deletes are not catched by incremental updates).

River can be created using:

	curl -XPUT localhost:9200/_river/my_remote_river/_meta -d '
	{
	    "type" : "remote",
	    "remote" : {
	        "urlGetDocuments"       : "https://system.org/rest/document?docSpace={space}&docUpdatedAfter={updatedAfter}",
	        "getDocsResFieldDocuments"  : "items"
	        "username"              : "remote_username",
          "pwd"                   : "remote_user_password",
	        "timeout"               : "5s",
	        "spacesIndexed"         : "ORG,AS7",
	        "spaceKeysExcluded"     : "",
	        "indexUpdatePeriod"     : "5m",
	        "indexFullUpdatePeriod" : "1h",
	        "maxIndexingThreads"    : 2,
	    },
	    "index" : {
	        "index" : "my_remote_index",
	        "type"  : "remote_document",
	        "remote_field_document_id" : "id",
	        "remote_field_updated" : "updated",
	    },
	    "activity_log": {
	        "index" : "remote_river_activity",
	        "type"  : "remote_river_indexupdate"
	    }
	}
	'

The example above lists all the main options controlling the creation and behavior of a Remote river. Full list of options with description is here:

* `remote/spacesIndexed` comma separated list of keys for remote system spaces to be indexed. Optional, list of spaces is obtained from remote system if omitted (so new spaces are indexed automatically).
* `remote/spaceKeysExcluded` comma separated list of keys for remote system spaces to be excluded from indexing if list is obtained from remote system (so used only if no `remote/spacesIndexed` is defined). Optional.
* `remote/indexUpdatePeriod`  time value, defines how often is search index updated from remote system. Optional, default 5 minutes.
* `remote/indexFullUpdatePeriod` time value, defines how often is search index updated from remote system in full update mode. Optional, default 12 hours. You can use `0` to disable automatic full updates. Full update updates all documents in search index from remote system, and removes documents deleted in remote system from search index also. This brings more load to both remote system and ElasticSearch servers, and may run for long time in case of remote systems with many documents. Incremental updates are performed between full updates as defined by `indexUpdatePeriod` parameter.
* `remote/maxIndexingThreads` defines maximal number of parallel indexing threads running for this river. Optional, default 1. This setting influences load on both JIRA and ElasticSearch servers during indexing. Threads are started per JIRA project update. If there is more threads allowed, then one is always dedicated for incremental updates only (so full updates do not block incremental updates for another projects).
* `remote/remoteClientClass` class implementing *remote system API client* used to pull data from remote system. See dedicated chapter later. Optional, *GET JSON remote system API client* used by default. Client class must implement [`org.jboss.elasticsearch.river.remote.IRemoteSystemClient`](/src/main/java/org/jboss/elasticsearch/river/remote/IRemoteSystemClient.java) interface.
* `remote/*` other params are used by the *remote system API client*
* `index/index` defines name of search [index](http://www.elasticsearch.org/guide/appendix/glossary.html#index) where documents from remote system are stored. Parameter is optional, name of river is used if omitted. See related notes later!
* `index/type` defines [type](http://www.elasticsearch.org/guide/appendix/glossary.html#type) used when document from remote system is stored into search index. Parameter is optional, `remote_document` is used if omitted. See related notes later!
* `index/field_river_name`, `index/field_space_key`, `index/field_document_id`, `index/fields`, `index/value_filters` are used to define structure of indexed document. See 'Index document structure' chapter.
* `index/remote_field_document_id` is used to define field in remote system document data where unique document identifier is stored. Dot notation may be used for deeper nesting in document data.
* `index/remote_field_updated` is used to define field in remote system document data where timestamp of last update is stored - timestamp may be formatted by ISO format or number representing millis from 1.1.1970. Dot notation may be used for deeper nesting in document data. 
* `index/comment_mode` defines mode of issue comments indexing: `none` - no comments indexed, `embedded` - comments indexed as array in document, `child` - comment indexed as separate document with [parent-child relation](http://www.elasticsearch.org/guide/reference/mapping/parent-field.html) to the document, `standalone` - comment indexed as separate document. Setting is optional, `none` value is default if not provided.
* `index/comment_type` defines [type](http://www.elasticsearch.org/guide/appendix/glossary.html#type) used when issue comment is stored into search index in `child` or `standalone` mode. See related notes later!
* `index/field_comments`, `index/comment_fields` can be used to change structure comment informations in indexed documents. See 'index document structure' chapter.
* `index/remote_field_comments` is used to define field in remote system document data where array of comments is stored. Dot notation may be used for deeper nesting in document data.
* `index/remote_field_comment_id` is used to define field in remote system's comment data where unique comment identifier is stored. Used if `comment_mode` is  `child` or `standalone`. Dot notation may be used for deeper nesting in document data.
* `index/preprocessors` optional parameter. Defines chain of preprocessors applied to document data read from remote system before stored into index. See related notes later!
* `activity_log` part defines where information about remote river index update activity are stored. If omitted then no activity informations are stored.
* `activity_log/index` defines name of index where information about remote river activity are stored.
* `activity_log/type` defines [type](http://www.elasticsearch.org/guide/appendix/glossary.html#type) used to store information about remote river activity. Parameter is optional, `remote_river_indexupdate` is used if omitted.

Time value in configuration is number representing milliseconds, but you can use these postfixes appended to the number to define units: `s` for seconds, `m` for minutes, `h` for hours, `d` for days and `w` for weeks. So for example value `5h` means five fours, `2w` means two weeks.
 
To get rid of some unwanted WARN log messages add next line to the [logging configuration file](http://www.elasticsearch.org/guide/reference/setup/configuration.html) of your ElasticSearch instance which is `config/logging.yml`:

	org.apache.commons.httpclient: ERROR

And to get rid of extensive INFO messages from index update runs use:

	org.jboss.elasticsearch.river.remote.SpaceByLastUpdateTimestampIndexer: WARN


Notes for Index and Document type mapping creation
--------------------------------------------------
Configured Search [index](http://www.elasticsearch.org/guide/appendix/glossary.html#index) 
is NOT explicitly created by river code. You have to [create it manually](http://www.elasticsearch.org/guide/reference/api/admin-indices-create-index.html) 
BEFORE river creation.

	curl -XPUT 'http://localhost:9200/my_remote_index/'

Type [Mapping](http://www.elasticsearch.org/guide/reference/mapping/) for document 
is not explicitly created by river code for configured document type. The river 
REQUIRES [Automatic Timestamp Field](http://www.elasticsearch.org/guide/reference/mapping/timestamp-field.html) 
and `keyword` analyzer for `space_key` and `source` fields to be able to 
correctly remove documents deleted in remote system from index during full update! 
So you have to create document type mapping manually BEFORE river creation, with next content at least:

	curl -XPUT localhost:9200/my_remote_index/remote_document/_mapping -d '
	{
	    "remote_document" : {
	        "_timestamp" : { "enabled" : true },
	        "properties" : {
	            "space_key" : {"type" : "string", "analyzer" : "keyword"},
	            "source"    : {"type" : "string", "analyzer" : "keyword"}
	        }
	    }
	}
	'

Same apply for 'comment' mapping if you use `child` or `standalone` mode!

	curl -XPUT localhost:9200/my_remote_index/remote_document_comment/_mapping -d '
	{
	    "remote_document_comment" : {
	        "_timestamp" : { "enabled" : true },
	        "properties" : {
	            "space_key" : {"type" : "string", "analyzer" : "keyword"},
	            "source"      : {"type" : "string", "analyzer" : "keyword"}
	        }
	    }
	}
	'

You can store [mappings in ElasticSearch node configuration](http://www.elasticsearch.org/guide/reference/mapping/conf-mappings.html) alternatively.

See next chapter for description of indexed document structure to create better mappings meeting your needs. 

If you use update activity logging then you can create index and mapping for it too:

	curl -XPUT 'http://localhost:9200/remote_river_activity/'
	curl -XPUT localhost:9200/remote_river_activity/remote_river_indexupdate/_mapping -d '
	{
	    "remote_river_indexupdate" : {
	        "properties" : {
	            "space_key" : {"type" : "string", "analyzer" : "keyword"},
	            "update_type" : {"type" : "string", "analyzer" : "keyword"},
	            "result"      : {"type" : "string", "analyzer" : "keyword"}
	         }
	    }
	}
	'

Remote system API to obtain data from 
-------------------------------------

###Remote system API requirements
Remote river uses two operations to obtain necessary data from remote system.

#####List Spaces
This operation is used to obtain list of Space keys from remote system. 
Each Space is then indexed independently, and partially in parallel, by the river.
Space key is passed to the "List documents" operation so remote system can return documents for given space. 

This operation is optional, `remote/spacesIndexed` configuration parameter can be used to define fixed set of space keys if you do not want to read them dynamically.
If your remote system do not support Space concept you can define `remote/spacesIndexed` with one value only representing all documents.    

####List Documents
This operation is used by indexer to obtain documents from remote system and store them into search index.
 
Number of documents returned from one call of this operation is not restricted,
but ideal value is between 10 and 100 documents. Indexer calls the operation multiple 
times and sets request parameters accordingly to obtain all necessary documents 
for both full and incremental update.   

Operation MUST accept and correctly handle these request parameters if provided by indexer: 

* `spaceKey` - remote system MUST return only documents for this space key (always provided by indexer)
* `updatedAfter` - remote system MUST return only documents updated at or after this timestamp (whole history if not provided by indexer)
* `startAtIndex` - remote system MUST return only documents matching previous two criteria, and starting at this index in result set. Support for this feature by remote system is optional, and is used only if remote system is able to return "total" count of matching documents in response. 

Operation MUST return these results:

* `documents` - list of documents with informations to be stored in search index. Unique identifier and 'last document update' timestamp must be present in data. Returned list MUST be ascending ordered by timestamp of last document update!
* `total count` - total number of documents matching request search criteria (but response may contain only part of them). Use of this feature is optional, some bulk updates in remote system may be missed if not used (because pooling is based only on updated timestamp in this case). If used then remote system MUST handle `startAtIndex` request parameter. 

###Remote system API clients
You can use some clients provided by river to use distinct remote system access technology and protocols, 
or you can create a new one by implementing [`org.jboss.elasticsearch.river.remote.IRemoteSystemClient`](/src/main/java/org/jboss/elasticsearch/river/remote/IRemoteSystemClient.java) interface.

#####GET JSON remote system API client
This is default remote system client implementation provided by river. 
Uses http/s GET requests to the target remote system and handles JSON response data. 
Configuration parameters for this client type:

* `remote/urlGetDocuments` is URL used to call *List Documents* operation from remote system. You may use three placeholders in this URL to be replaced by parameters required by indexing process as described above: 
  * `{space}` - remote system must return only documents for this space 
  * `{updatedAfter}` - remote system must return only documents updated after this timestamp, number representing as millis from 1.1.1970
  * `{startAtIndex}` - remote system must return only documents matching previous two criteria, and starting at this index in result set.  
* `remote/getDocsResFieldDocuments` defines field in JSON data returned from `remote/urlGetDocuments` call, where array of documents is stored. If not defined then the array is expected directly in the root of returned data. Dot notation may be used for deeper nesting in the JSON structure.
* `remote/getDocsResFieldTotalcount` defines field in JSON data returned from `remote/urlGetDocuments` call, where total number of documents matching passed search criteria is stored. Dot notation may be used for deeper nesting in the JSON structure. 
* `remote/username` and `remote/pwd` are optional login credentials to access documents in remote system. HTTP BASIC authentication is supported. Alternatively you can store password into separate JSON document called `_pwd` stored in the rived index beside `_meta` document, into field called `pwd`, see example later.
* `remote/timeout` time value, defines timeout for http/s request to the remote system. Optional, 5s is default if not provided.
* `remote/urlGetSpaces` is URL used to call *List Spaces* operation from remote system. Necessary if `remote/spacesIndexed` is not provided.
* `remote/getSpacesResField` defines field in JSON data returned from `remote/urlGetSpaces` call, where array of space keys is stored. If not defined then the array is expected directly in root of returned data. Dot notation may be used for deeper nesting in the JSON structure.

Password can be created using:

	curl -XPUT localhost:9200/_river/my_remote_river/_pwd -d '{"pwd" : "mypassword"}'


Indexed document structure
--------------------------
You can configure which fields from document obtained from remote system will be available in search
index and under which names. See [`river_configuration_default.json`](/src/main/resources/templates/river_configuration_default.json) 
and [`river_configuration_example.json`](/src/main/resources/examples/river_configuration_example.json)
file for example of river configuration, which is used to create default configuration.

Remote River writes JSON document with following structure to the search index
for document. Remote document structure must provide unique identifier to be used
as document [id](http://www.elasticsearch.org/guide/appendix/glossary.html#id) in 
search index. You can configure this field name over `index/remote_field_document_id` setting.
You can use dot notation for deeper nesting of

    --------------------------------------------------------------------------------------------------------------------------------------------------------
    | **index field** | **indexed field value notes**                 | **river configuration for index field** | **river configuration for source field** |
    --------------------------------------------------------------------------------------------------------------------------------------------------------
    | source          | name of the river the document was indexed by | index/field_river_name                  | N/A                                      |
    --------------------------------------------------------------------------------------------------------------------------------------------------------
    | space_key       | key of Space the document is for              | index/field_space_key                   | N/A                                      |
    --------------------------------------------------------------------------------------------------------------------------------------------------------
    | document_id     | id of the document                            | index/field_document_id                 | index/remote_field_document_id           |
    --------------------------------------------------------------------------------------------------------------------------------------------------------
    | all others      | all other values for the document             | index/fields/*                          | index/fields/*/remote_field              |
    --------------------------------------------------------------------------------------------------------------------------------------------------------
    | from config     | Array of comments if `embedded` mode is used  | index/field_comments                    | index/remote_field_comments              |
    --------------------------------------------------------------------------------------------------------------------------------------------------------

Array of comments is taken from document structure from field defined in `index/remote_field_comments` configuration. 
Remote River uses following structure to store comment informations into search index. 
Comment id is taken from field configured in `index/remote_field_comment_id` and is used as document 
[id](http://www.elasticsearch.org/guide/appendix/glossary.html#id) in search index in `child` or `standalone` mode.

    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    | **index field** | **indexed field value notes**                                        | **river configuration for index field** | **river configuration for source field** |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    | source          | name of the river the comment was indexed by, not in `embedded` mode | index/field_river_name                  | N/A                                      |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    | space_key       | key of documents's space the comment is for, not in `embedded` mode  | index/field_space_key                   | N/A                                      |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    | document_id     | id of the document the comment is for, not in `embedded` mode        | index/field_document_id                 | index/remote_field_document_id           |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    | all others      | all other values for the comment are mapped by river configuration   | index/comment_fields/*                  | index/comment_fields/*/remote_field      | 
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

You can also implement and configure some preprocessors, which allows you to 
change/extend document informations loaded from remote system and store 
these changes/extensions to the search index.
This allows you for example value normalizations, or creation of some index 
fields with values aggregated from more document fields.

Framework called [structured-content-tools](https://github.com/jbossorg/structured-content-tools) 
is used to implement these preprocessors. Example how to configure preprocessors is available 
[here](/src/main/resources/examples/river_configuration_example.json).
Some generic configurable preprocessor implementations are available as part of 
the [structured-content-tools framework](https://github.com/jbossorg/structured-content-tools).

Index structure creation is implemented by [org.jboss.elasticsearch.river.remote.DocumentWithCommentsIndexStructureBuilder](/src/main/java/org/jboss/elasticsearch/river/remote/DocumentWithCommentsIndexStructureBuilder.java)

Management REST API
-------------------
Remote river supports next REST commands for management purposes. Note 
`my_remote_river` in examples is name of the remote river you can call operation
for, so replace it with real name for your calls.

Get [state info](/src/main/resources/examples/mgm/rest_river_info.json) about
the river operation:

	curl -XGET localhost:9200/_river/my_remote_river/_mgm_rr/state

Stop remote river indexing process. Process is stopped permanently, so even
after complete elasticsearch cluster restart or river migration to another 
node. You need to `restart` it over management REST API (see next command):

	curl -XPOST localhost:9200/_river/my_remote_river/_mgm_rr/stop

Restart remote river indexing process. Configuration of river is reloaded during 
restart. You can restart running indexing, or stopped indexing (see previous command):

	curl -XPOST localhost:9200/_river/my_remote_river/_mgm_rr/restart

Force full index update for all document spaces:

	curl -XPOST localhost:9200/_river/my_remote_river/_mgm_rr/fullupdate

Force full index update of documents for Space with key provided in `spaceKey`:

	curl -XPOST localhost:9200/_river/my_remote_river/_mgm_rr/fullupdate/spaceKey

List names of all Remote Rivers running in ES cluster:

	curl -XGET localhost:9200/_remote_river/list


License
-------

    This software is licensed under the Apache 2 license, quoted below.

    Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors as indicated by the @authors tag. 
    All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
