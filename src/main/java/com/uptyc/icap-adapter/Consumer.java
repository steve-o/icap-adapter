/* Simple consumer.
 */

package com.uptyc.IcapAdapter;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Joiner;
import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConnectionEvent;
import com.reuters.rfa.session.omm.OMMConnectionIntSpec;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.dictionary.RDMDictionary;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.dictionary.RDMDictionaryCache;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.dictionary.RDMDictionaryRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.dictionary.RDMDictionaryRequestAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.dictionary.RDMDictionaryResponse;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectory;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryRequestAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryResponse;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryResponsePayload;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLogin;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginRequestAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginResponse;

public class Consumer implements Client {
	private static Logger LOG = LogManager.getLogger (Consumer.class.getName());

	private SessionConfig config;

/* RFA context. */
	private Rfa rfa;

/* RFA asynchronous event queue. */
	private EventQueue event_queue;

/* RFA session defines one or more connections for horizontal scaling. */
	private Session session;

/* RFA OMM consumer interface. */
	private OMMConsumer omm_consumer;
        private OMMPool omm_pool;
	private OMMEncoder omm_encoder;

/* Data dictionaries. */
	private RDMDictionaryCache rdm_dictionary;

/* Directory */
	private Map<String, ItemStream> directory;

/* RFA Item event consumer */
	private Handle login_handle;
	private Handle directory_handle;

	private class FlaggedHandle {
		private Handle handle;
		private boolean flag;

		public FlaggedHandle (Handle handle) {
			this.handle = handle;
			this.flag = false;
		}

		public Handle getHandle() {
			return this.handle;
		}

		public boolean isFlagged() {
			return this.flag;
		}

		public void setFlag() {
			this.flag = true;
		}
	}

	private Map<String, FlaggedHandle> dictionary_handle;

/* Reuters Wire Format versions. */
	private byte rwf_major_version;
	private byte rwf_minor_version;

	private boolean is_muted;
	private boolean pending_directory;
	private boolean pending_dictionary;

	private static final int OMM_PAYLOAD_SIZE       = 5000;

	private static final String RSSL_PROTOCOL       = "rssl";
	private static final String SSLED_PROTOCOL      = "ssled";

	private static final String RDM_FIELD_DICTIONARY_NAME = "RWFFld";
	private static final String RDM_ENUMTYPE_DICTIONARY_NAME = "RWFEnum";

	public Consumer (SessionConfig config, Rfa rfa, EventQueue event_queue) {
		this.config = config;
		this.rfa = rfa;
		this.event_queue = event_queue;
		this.rwf_major_version = 0;
		this.rwf_minor_version = 0;
		this.is_muted = true;
		this.pending_directory = true;
		this.pending_dictionary = true;
	}

	public void init() throws Exception {
		LOG.info ("{}", this.config);
/* Configuring the session layer package.
 */
		LOG.info ("Acquiring RFA session.");
		this.session = Session.acquire (this.config.getSessionName());

/* RFA Version Info. The version is only available if an application
 * has acquired a Session (i.e., the Session Layer library is laoded).
 */
		LOG.info ("RFA: { \"productVersion\": \"{}\" }", Context.getRFAVersionInfo().getProductVersion());

		if (this.config.getProtocol().equalsIgnoreCase (RSSL_PROTOCOL))
		{
/* Initializing an OMM consumer. */
			LOG.info ("Creating OMM consumer.");
			this.omm_consumer = (OMMConsumer)this.session.createEventSource (EventSource.OMM_CONSUMER,
						this.config.getConsumerName(),
						false /* complete events */);
/* OMM memory management. */
			this.omm_pool = OMMPool.create();
			this.omm_encoder = this.omm_pool.acquireEncoder();
			this.omm_encoder.initialize (OMMTypes.MSG, OMM_PAYLOAD_SIZE);

			this.rdm_dictionary = new RDMDictionaryCache();

			this.sendLoginRequest();
			this.sendDirectoryRequest();
		}
		else if (this.config.getProtocol().equalsIgnoreCase (SSLED_PROTOCOL))
		{
/* Initializing a Market Data Subscriber. */
		}
		else
		{
			throw new Exception ("Unsupported transport protocol \"" + this.config.getProtocol() + "\".");
		}

		this.directory = new LinkedHashMap<String, ItemStream>();
		this.dictionary_handle = new TreeMap<String, FlaggedHandle>();
	}

	public void clear() {
	}

/* Create an item stream for a given symbol name.  The Item Stream maintains
 * the provider state on behalf of the application.
 */
	public void createItemStream (Instrument instrument, ItemStream item_stream) {
		LOG.info ("Creating item stream for RIC \"{}\" on service \"{}\".", instrument.getName(), instrument.getService());
		item_stream.setItemName (instrument.getName());
		item_stream.setServiceName (instrument.getService());

		StringBuilder key = new StringBuilder (instrument.getService());
		key.append ('.');
		key.append (instrument.getName());
		if (!this.is_muted) {
			this.sendItemRequest (item_stream);
		}
		this.directory.put (key.toString(), item_stream);
		LOG.info ("Directory size: {}", this.directory.size());
	}

	public void resubscribe() {
		if (this.config.getProtocol().equalsIgnoreCase (RSSL_PROTOCOL))
		{
			if (null == this.omm_consumer) {
				LOG.warn ("Resubscribe whilst consumer is invalid.");
				return;
			}

			for (ItemStream item_stream : this.directory.values()) {
				if (!item_stream.hasItemHandle())
					this.sendItemRequest (item_stream);
			}
		}
	}

	private void sendItemRequest (ItemStream item_stream) {
		LOG.info ("Sending market price request.");
		OMMMsg msg = this.omm_pool.acquireMsg();
		msg.setMsgType (OMMMsg.MsgType.REQUEST);
		msg.setMsgModelType (RDMMsgTypes.MARKET_PRICE);
		msg.setAssociatedMetaInfo (this.login_handle);
		msg.setIndicationFlags (OMMMsg.Indication.REFRESH);
		msg.setAttribInfo (item_stream.getServiceName(), item_stream.getItemName(), RDMInstrument.NameType.RIC);

		LOG.info ("Registering OMM item interest for MMT_MARKET_PRICE");
		OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
		ommItemIntSpec.setMsg (msg);
		item_stream.setItemHandle (this.omm_consumer.registerClient (this.event_queue, ommItemIntSpec, this, null));
		this.omm_pool.releaseMsg (msg);
	}

/* Making a Login Request
 * A Login request message is encoded and sent by OMM Consumer and OMM non-
 * interactive provider applications.
 */
	private void sendLoginRequest() {
		LOG.info ("Sending login request.");
		RDMLoginRequest request = new RDMLoginRequest();
		RDMLoginRequestAttrib attribInfo = new RDMLoginRequestAttrib();

/* RFA/Java only.
 */
		request.setMessageType (RDMLoginRequest.MessageType.REQUEST);
		request.setIndicationMask (EnumSet.of (RDMLoginRequest.IndicationMask.REFRESH));
		attribInfo.setRole (RDMLogin.Role.CONSUMER);

/* DACS username.
 */
		attribInfo.setNameType (RDMLogin.NameType.USER_NAME);
		attribInfo.setName (this.config.getUserName());

/* DACS Application Id.
 * e.g. "256"
 */
		attribInfo.setApplicationId (this.config.getApplicationId());

/* DACS Position name.
 * e.g. "localhost"
 */
		attribInfo.setPosition (this.config.getPosition());

/* Instance Id (optional).
 * e.g. "<Instance Id>"
 */
		if (this.config.hasInstanceId()) {
			attribInfo.setInstanceId (this.config.getInstanceId());
		}

		request.setAttrib (attribInfo);

		LOG.info ("Registering OMM item interest for MMT_LOGIN.");
		OMMMsg msg = request.getMsg (this.omm_pool);
		OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
		ommItemIntSpec.setMsg (msg);
		this.login_handle = this.omm_consumer.registerClient (this.event_queue, ommItemIntSpec, this, null);

/* Reset status */
		this.pending_directory = true;
		this.pending_dictionary = true;
	}

/* Make a directory request to see if we can ask for a dictionary.
 */
	private void sendDirectoryRequest() {
		LOG.info ("Sending directory request.");
		RDMDirectoryRequest request = new RDMDirectoryRequest();
		RDMDirectoryRequestAttrib attribInfo = new RDMDirectoryRequestAttrib();

/* RFA/Java only.
 */
		request.setMessageType (RDMDirectoryRequest.MessageType.REQUEST);
		request.setIndicationMask (EnumSet.of (RDMDirectoryRequest.IndicationMask.REFRESH));

/* Limit to named service */
		attribInfo.setServiceName (this.config.getServiceName());

/* Only request INFO filters */
		attribInfo.setFilterMask (EnumSet.of (RDMDirectory.FilterMask.INFO));

		request.setAttrib (attribInfo);

		LOG.info ("Registering OMM item interest for MMT_DIRECTORY.");
		OMMMsg msg = request.getMsg (this.omm_pool);
		OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
		ommItemIntSpec.setMsg (msg);
		this.directory_handle = this.omm_consumer.registerClient (this.event_queue, ommItemIntSpec, this, null);
	}

/* Make a dictionary request.
 *
 * 5.8.3 Version Check
 * Dictionary version checking can be performed by the client after a refresh
 * (Section 2.2) response message of a Dictionary is received.
 */
	private void sendDictionaryRequest (String dictionary_name) {
		LOG.info ("Sending dictionary request for \"{}\".", dictionary_name);
		RDMDictionaryRequest request = new RDMDictionaryRequest();
		RDMDictionaryRequestAttrib attribInfo = new RDMDictionaryRequestAttrib();

/* RFA/Java only.
 */
		request.setMessageType (RDMDictionaryRequest.MessageType.REQUEST);
		request.setIndicationMask (EnumSet.of (RDMDictionaryRequest.IndicationMask.REFRESH));

// RDMDictionary.Filter.NORMAL=0x7: Provides all information needed for decoding
		attribInfo.setVerbosity (RDMDictionary.Verbosity.NORMAL);
		attribInfo.setDictionaryName (dictionary_name);
		attribInfo.setServiceName (this.config.getServiceName());

		request.setAttrib (attribInfo);

		LOG.info ("Registering OMM item interest for MMT_DICTIONARY/{}.", dictionary_name);
		OMMMsg msg = request.getMsg (this.omm_pool);
		OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
		ommItemIntSpec.setMsg (msg);
		this.dictionary_handle.put (dictionary_name,
			new FlaggedHandle (this.omm_consumer.registerClient (this.event_queue, ommItemIntSpec, this, dictionary_name /* closure */)));
	}

	@Override
	public void processEvent (Event event) {
		LOG.info ("{}", event);
		switch (event.getType()) {
		case Event.OMM_ITEM_EVENT:
			this.OnOMMItemEvent ((OMMItemEvent)event);
			break;

		case Event.OMM_CONNECTION_EVENT:
			this.OnConnectionEvent ((OMMConnectionEvent)event);
			break;

		default:
			LOG.warn ("Uncaught: {}", event);
			break;
		}
	}

/* Handling Item Events, message types are munged c.f. C++ API.
 */
	private void OnOMMItemEvent (OMMItemEvent event) {
LOG.info ("OnOMMItemEvent: {}", event);
		final OMMMsg msg = event.getMsg();

/* Verify event is a response event. */
		switch (msg.getMsgType()) {
		case OMMMsg.MsgType.REFRESH_RESP:
		case OMMMsg.MsgType.UPDATE_RESP:
		case OMMMsg.MsgType.STATUS_RESP:
		case OMMMsg.MsgType.ACK_RESP:
			this.OnRespMsg (msg, event.getHandle(), event.getClosure());
			break;

/* Request message */
		case OMMMsg.MsgType.REQUEST:
/* Generic message */
		case OMMMsg.MsgType.GENERIC:
/* Post message */
		case OMMMsg.MsgType.POST:
		default:
			LOG.warn ("Uncaught: {}", msg);
			break;
		}
	}

	private void OnRespMsg (OMMMsg msg, Handle handle, Object closure) {
LOG.info ("OnRespMsg: {}", msg);
		switch (msg.getMsgModelType()) {
		case RDMMsgTypes.LOGIN:
			this.OnLoginResponse (msg);
			break;

		case RDMMsgTypes.DIRECTORY:
			this.OnDirectoryResponse (msg);
			break;

		case RDMMsgTypes.DICTIONARY:
			this.OnDictionaryResponse (msg, handle, closure);
			break;

		case RDMMsgTypes.MARKET_PRICE:
			this.OnMarketPrice (msg);
			break;

		default:
			LOG.warn ("Uncaught: {}", msg);
			break;
		}
	}

	private void OnLoginResponse (OMMMsg msg) {
LOG.info ("OnLoginResponse: {}", msg);
		final RDMLoginResponse response = new RDMLoginResponse (msg);
		final byte stream_state = response.getRespStatus().getStreamState();
		final byte data_state   = response.getRespStatus().getDataState();

		switch (stream_state) {
		case OMMState.Stream.OPEN:
			switch (data_state) {
			case OMMState.Data.OK:
				this.OnLoginSuccess (response);
				break;

			case OMMState.Data.SUSPECT:
				this.OnLoginSuspect (response);
				break;

			default:
				LOG.warn ("Uncaught data state: {}", response);
				break;
			}
			break;

		case OMMState.Stream.CLOSED:
			this.OnLoginClosed (response);
			break;

		default:
			LOG.warn ("Uncaught stream state: {}", response);
			break;
		}
	}

/* Login Success.
 */
	private void OnLoginSuccess (RDMLoginResponse response) {
LOG.info ("OnLoginSuccess: {}", response);
		LOG.info ("Unmuting consumer.");
		this.is_muted = false;
	}

/* Other Login States.
 */
	private void OnLoginSuspect (RDMLoginResponse response) {
LOG.info ("OnLoginSuspect: {}", response);
		this.is_muted = true;
	}

/* Other Login States.
 */
	private void OnLoginClosed (RDMLoginResponse response) {
LOG.info ("OnLoginClosed: {}", response);
		this.is_muted = true;
	}

/* MMT_DIRECTORY domain.
 */
	private void OnDirectoryResponse (OMMMsg msg) {
LOG.info ("OnDirectoryResponse: {}", msg);
//GenericOMMParser.parse (msg);
//		final RDMDirectoryResponse response = new RDMDirectoryResponse (msg);
/* Received */
/*
		if (response.hasPayload()) {
			final RDMDirectoryResponsePayload payload = response.getPayload();
			if (payload.hasServiceList()) {
				Iterator<Service> it = payload.getServiceList().iterator();
				while (it.hasNext()) {
					final Service service = it.next();
					LOG.info ("Service: {}", service.getServiceName());
				}

				this.pending_directory = false;
			}
		} */

/* WORKAROUND: request each listed "used" dictionaries.
 */
		if ((OMMMsg.MsgType.REFRESH_RESP == msg.getMsgType()
			|| OMMMsg.MsgType.UPDATE_RESP == msg.getMsgType())
/* check for payload */
			&& OMMTypes.NO_DATA != msg.getDataType())
		{
			final OMMMap map = (OMMMap)msg.getPayload();
			for (Iterator<?> it = map.iterator(); it.hasNext();) {
				final OMMMapEntry map_entry = (OMMMapEntry)it.next();
				if (OMMTypes.FILTER_LIST != map_entry.getDataType()) {
					LOG.info ("OMM map entry not a filter list.");
					continue;
				}
				final OMMFilterList filter_list = (OMMFilterList)map_entry.getData();
/* extract out INFO filter */
				OMMFilterEntry info_filter = null;
				for (Iterator<?> filter = filter_list.iterator(); filter.hasNext();) {
					OMMFilterEntry filter_entry = (OMMFilterEntry)filter.next();
					if (filter_entry.getFilterId() == RDMService.FilterId.INFO) {
						info_filter = filter_entry;
						break;
					}
				}
				if (null == info_filter) {
					LOG.info ("OMM filter list contains no INFO filter.");
					continue;
				}
				LOG.info ("OMM filter list contains INFO filter.");
				if (null == info_filter || OMMTypes.ELEMENT_LIST != info_filter.getDataType()) {
					LOG.info ("INFO filter is not an OMM element list.");
					continue;
				}
				final OMMElementList element_list = (OMMElementList)info_filter.getData();
				for (Iterator<?> jt = element_list.iterator(); jt.hasNext();) {
					final OMMElementEntry element_entry = (OMMElementEntry)jt.next();
					final OMMData element_data = element_entry.getData();
					if (!element_entry.getName().equals (com.reuters.rfa.rdm.RDMService.Info.DictionariesUsed))
						continue;
					LOG.info ("Found DictionariesUsed entry");
					if (OMMTypes.ARRAY != element_data.getType()) {
						LOG.info ("DictionariesUsed not an OMM array");
						continue;
					}
					final OMMArray array = (OMMArray)element_data;
					Iterator<?> kt = array.iterator();
					while (kt.hasNext()) {
						final OMMEntry array_entry = (OMMEntry)kt.next();
						final String dictionary_name = array_entry.getData().toString();
						if (!this.dictionary_handle.containsKey (dictionary_name))
							this.sendDictionaryRequest (dictionary_name);
						LOG.info ("Used dictionary: {}", dictionary_name);
					}
				}
			}

/* directory received. */
			this.pending_directory = false;
		}
	}

/* MMT_DICTIONARY domain.
 *
 * 5.8.4 Streaming Dictionary
 * Dictionary request can be streaming. Dictionary providers are not allowed to
 * send refresh and update data to consumers.  Instead the provider can
 * advertise a minor Dictionary change by sending a status (Section 2.2)
 * response message with a DataState of Suspect. It is the consumer’s
 * responsibility to reissue the dictionary request.
 */
	private void OnDictionaryResponse (OMMMsg msg, Handle handle, Object closure) {
LOG.info ("OnDictionaryResponse: {}", msg);
		final RDMDictionaryResponse response = new RDMDictionaryResponse (msg);
/* Receiving dictionary */
		if (response.getMessageType() == RDMDictionaryResponse.MessageType.REFRESH_RESP
			&& response.hasPayload() && null != response.getPayload())
		{
			if (response.hasAttrib()) {
				LOG.info ("Dictionary: {}", response.getAttrib().getDictionaryName());
			}
			this.rdm_dictionary.load (response.getPayload(), handle);
		}

/* Only know type after it is loaded. */
		final RDMDictionary.DictionaryType dictionary_type = this.rdm_dictionary.getDictionaryType (handle);

/* Received complete dictionary */
		if (response.getMessageType() == RDMDictionaryResponse.MessageType.REFRESH_RESP
			&& response.getIndicationMask().contains (RDMDictionaryResponse.IndicationMask.REFRESH_COMPLETE))
		{
			LOG.info ("Dictionary complete.");
/* Check dictionary version */
			FieldDictionary field_dictionary = this.rdm_dictionary.getFieldDictionary();
			if (RDMDictionary.DictionaryType.RWFFLD == dictionary_type)
			{
				LOG.info ("RDMFieldDictionary version: {}", field_dictionary.getFieldProperty ("Version"));
			}
			else if (RDMDictionary.DictionaryType.RWFENUM == dictionary_type)
			{
				LOG.info ("enumtype.def version: {}", field_dictionary.getEnumProperty ("Version"));
			}
			GenericOMMParser.initializeDictionary (field_dictionary);

			this.dictionary_handle.get ((String)closure).setFlag();

/* Check all pending dictionaries */
			int pending_dictionaries = this.dictionary_handle.size();
			for (FlaggedHandle flagged_handle : this.dictionary_handle.values()) {
				if (flagged_handle.isFlagged())
					--pending_dictionaries;
			}
			if (0 == pending_dictionaries) {
				LOG.info ("All used dictionaries loaded, resuming subscriptions.");
				this.resubscribe();
				this.pending_dictionary = false;
			} else {
				LOG.info ("Dictionaries pending: {}", pending_dictionaries);
			}
		}
	}

/* MMT_MARKETPRICE domain.
 */
	private void OnMarketPrice (OMMMsg msg) {
		GenericOMMParser.parse (msg);
	}


	private void OnConnectionEvent (OMMConnectionEvent event) {
LOG.info ("OnConnectionEvent: {}", event);
	}
}

/* eof */
