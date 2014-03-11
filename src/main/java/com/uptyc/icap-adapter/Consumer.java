/* Simple consumer.
 */

package com.uptyc.IcapAdapter;

import java.util.*;
import java.net.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.joda.time.DateTime;
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
import com.reuters.rfa.omm.OMMDataBuffer;
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
import com.reuters.rfa.session.event.ConnectionEvent;
import com.reuters.rfa.session.event.EntitlementsAuthenticationEvent;
import com.reuters.rfa.session.event.MarketDataItemEvent;
import com.reuters.rfa.session.event.MarketDataItemStatus;
import com.reuters.rfa.session.event.MarketDataSvcEvent;
import com.reuters.rfa.session.event.MarketDataSvcStatus;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.MarketDataEnums;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.rfa.session.MarketDataSubscriberInterestSpec;
import com.reuters.rfa.session.omm.OMMConnectionEvent;
import com.reuters.rfa.session.omm.OMMConnectionIntSpec;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.tibmsg.TibException;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;
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
	private static final Marker ICAP_MARKER = MarkerManager.getMarker ("ICAP");

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

/* RFA market data subscriber interface. */
	private MarketDataSubscriber market_data_subscriber;
	private TibMsg msg;
	private TibField field;

/* Data dictionaries. */
	private RDMDictionaryCache rdm_dictionary;

/* Directory */
	private Map<String, ItemStream> directory;

/* RFA Item event consumer */
	private Handle error_handle;
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

	private static final boolean UNSUBSCRIBE_ON_SHUTDOWN = false;

	private static final int OMM_PAYLOAD_SIZE       = 5000;

	private static final String RSSL_PROTOCOL       = "rssl";
	private static final String SSLED_PROTOCOL      = "ssled";

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
		LOG.trace (this.config);
/* Configuring the session layer package.
 */
		LOG.trace ("Acquiring RFA session.");
		this.session = Session.acquire (this.config.getSessionName());

/* RFA Version Info. The version is only available if an application
 * has acquired a Session (i.e., the Session Layer library is laoded).
 */
		LOG.debug ("RFA: { \"productVersion\": \"{}\" }", Context.getRFAVersionInfo().getProductVersion());

		if (this.config.getProtocol().equalsIgnoreCase (RSSL_PROTOCOL))
		{
/* Initializing an OMM consumer. */
			LOG.trace ("Creating OMM consumer.");
			this.omm_consumer = (OMMConsumer)this.session.createEventSource (EventSource.OMM_CONSUMER,
						this.config.getConsumerName(),
						false /* complete events */);

/* Registering for Events from an OMM Consumer. */
			LOG.trace ("Registering OMM error interest.");
			OMMErrorIntSpec ommErrorIntSpec = new OMMErrorIntSpec();
			this.error_handle = this.omm_consumer.registerClient (this.event_queue, ommErrorIntSpec, this, null);

/* OMM memory management. */
			this.omm_pool = OMMPool.create (OMMPool.SINGLE_THREADED);
			this.omm_encoder = this.omm_pool.acquireEncoder();
			this.omm_encoder.initialize (OMMTypes.MSG, OMM_PAYLOAD_SIZE);

			this.rdm_dictionary = new RDMDictionaryCache();

			this.sendLoginRequest();
			this.sendDirectoryRequest();
		}
		else if (this.config.getProtocol().equalsIgnoreCase (SSLED_PROTOCOL))
		{
/* Initializing a Market Data Subscriber. */
			LOG.trace ("Creating market data subscriber.");
			this.market_data_subscriber = (MarketDataSubscriber)this.session.createEventSource (EventSource.MARKET_DATA_SUBSCRIBER,
						this.config.getConsumerName(),
						false /* complete events */);

			LOG.trace ("Registering market data status interest.");
			MarketDataSubscriberInterestSpec marketDataSubscriberInterestSpec = new MarketDataSubscriberInterestSpec();
			marketDataSubscriberInterestSpec.setMarketDataSvcInterest (true);
			marketDataSubscriberInterestSpec.setConnectionInterest (false);
			marketDataSubscriberInterestSpec.setEntitlementsInterest (false);
			this.error_handle = this.market_data_subscriber.registerClient (this.event_queue, marketDataSubscriberInterestSpec, this, null);

/* TibMsg memory management. */
			this.msg = new TibMsg();
			this.field = new TibField();
		}
		else
		{
			throw new Exception ("Unsupported transport protocol \"" + this.config.getProtocol() + "\".");
		}

		this.directory = new LinkedHashMap<String, ItemStream>();
		this.dictionary_handle = new TreeMap<String, FlaggedHandle>();
	}

	public void clear() {
		if (null != this.market_data_subscriber) {
			LOG.trace ("Closing MarketDataSubscriber.");
			if (UNSUBSCRIBE_ON_SHUTDOWN) {
/* 9.9.3 Upstream Batching
 * Market Data Subscriber’s unsubscribeAll() can be used to encourage RFA Java to batch unsubscribe
 * requests on connections that support batching of those requests into a message.
 */
				this.market_data_subscriber.unsubscribeAll();
				if (null != this.directory && !this.directory.isEmpty())
					this.directory.clear();
				if (null != this.error_handle) {
					this.market_data_subscriber.unregisterClient (this.error_handle);
					this.error_handle = null;
				}
			} else {
				if (null != this.directory && !this.directory.isEmpty())
					this.directory.clear();
				if (null != this.error_handle)
					this.error_handle = null;
			}
			this.market_data_subscriber.destroy();
			this.market_data_subscriber = null;
		}
		if (null != this.rdm_dictionary)
			this.rdm_dictionary = null;
		if (null != this.omm_encoder)
			this.omm_encoder = null;
		if (null != this.omm_pool) {
			LOG.trace ("Closing OMMPool.");
			this.omm_pool.destroy();
			this.omm_pool = null;
		}
		if (null != this.omm_consumer) {
			LOG.trace ("Closing OMMConsumer.");
/* 8.2.11 Shutting Down an Application
 * an application may just destroy Event
 * Source, in which case the closing of the streams is handled by the RFA.
 */
			if (UNSUBSCRIBE_ON_SHUTDOWN) {
/* 9.2.5.3 Batch Close
 * The consumer application
 * builds a List of Handles of the event streams to close and calls OMMConsumer.unregisterClient().
 */
				if (null != this.directory && !this.directory.isEmpty()) {
					List<Handle> item_handles = new ArrayList<Handle> (this.directory.size());
					for (ItemStream item_stream : this.directory.values()) {
						if (item_stream.hasItemHandle())
							item_handles.add (item_stream.getItemHandle());
					}
					this.omm_consumer.unregisterClient (item_handles, null);
					this.directory.clear();
				}
				if (null != this.dictionary_handle && !this.dictionary_handle.isEmpty()) {
					for (FlaggedHandle flagged_handle : this.dictionary_handle.values()) {
						this.omm_consumer.unregisterClient (flagged_handle.getHandle());
					}
					this.dictionary_handle.clear();
				}
				if (null != this.directory_handle) {
					this.omm_consumer.unregisterClient (this.directory_handle);
					this.directory_handle = null;
				}
				if (null != this.login_handle) {
					this.omm_consumer.unregisterClient (this.login_handle);
					this.login_handle = null;
				}
			} else {
				if (null != this.directory && !this.directory.isEmpty())
					this.directory.clear();
				if (null != this.dictionary_handle && !this.dictionary_handle.isEmpty())
					this.dictionary_handle.clear();
				if (null != this.directory_handle)
					this.directory_handle = null;
				if (null != this.login_handle)
					this.login_handle = null;
			}
			this.omm_consumer.destroy();
			this.omm_consumer = null;
		}
		if (null != this.session) {
			LOG.trace ("Closing RFA Session.");
			this.session.release();
			this.session = null;
		}
	}

/* Create an item stream for a given symbol name.  The Item Stream maintains
 * the provider state on behalf of the application.
 */
	public void createItemStream (Instrument instrument, ItemStream item_stream) {
		LOG.trace ("Creating item stream for RIC \"{}\" on service \"{}\".", instrument.getName(), instrument.getService());
		item_stream.setItemName (instrument.getName());
		item_stream.setServiceName (instrument.getService());
/* viewType:- RDMUser.View.FIELD_ID_LIST or RDMUser.View.ELEMENT_NAME_LIST */
		item_stream.setView (instrument.getFields());

		StringBuilder key = new StringBuilder()
					.append (instrument.getService())
					.append ('.')
					.append (instrument.getName());
		if (!this.is_muted) {
			if (this.config.getProtocol().equalsIgnoreCase (RSSL_PROTOCOL))
				this.sendItemRequest (item_stream);
			else if (this.config.getProtocol().equalsIgnoreCase (SSLED_PROTOCOL))
				this.addSubscription (item_stream);
		}
		this.directory.put (key.toString(), item_stream);
		LOG.trace ("Directory size: {}", this.directory.size());
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
		else if (this.config.getProtocol().equalsIgnoreCase (SSLED_PROTOCOL))
		{
			if (null == this.market_data_subscriber) {
				LOG.warn ("Resubscribe whilst subscriber is invalid.");
				return;
			}

			for (ItemStream item_stream : this.directory.values()) {
				if (!item_stream.hasItemHandle())
					this.addSubscription (item_stream);
			}
		}
	}

	private void sendItemRequest (ItemStream item_stream) {
		LOG.trace ("Sending market price request.");
		OMMMsg msg = this.omm_pool.acquireMsg();
		msg.setMsgType (OMMMsg.MsgType.REQUEST);
		msg.setMsgModelType (RDMMsgTypes.MARKET_PRICE);
		msg.setAssociatedMetaInfo (this.login_handle);
		msg.setIndicationFlags (OMMMsg.Indication.REFRESH);
		msg.setAttribInfo (item_stream.getServiceName(), item_stream.getItemName(), RDMInstrument.NameType.RIC);

		LOG.trace ("Registering OMM item interest for MMT_MARKET_PRICE.");
		OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
		ommItemIntSpec.setMsg (msg);
		item_stream.setItemHandle (this.omm_consumer.registerClient (this.event_queue, ommItemIntSpec, this, item_stream));
		this.omm_pool.releaseMsg (msg);
	}

	private void addSubscription (ItemStream item_stream) {
		LOG.trace ("Adding market data subscription.");
		MarketDataItemSub marketDataItemSub = new MarketDataItemSub();
		marketDataItemSub.setServiceName (item_stream.getServiceName());
		marketDataItemSub.setItemName (item_stream.getItemName());
		this.market_data_subscriber.subscribe (this.event_queue, marketDataItemSub, this, item_stream);
	}

/* Making a Login Request
 * A Login request message is encoded and sent by OMM Consumer and OMM non-
 * interactive provider applications.
 */
	private void sendLoginRequest() throws UnknownHostException {
		LOG.trace ("Sending login request.");
		RDMLoginRequest request = new RDMLoginRequest();
		RDMLoginRequestAttrib attribInfo = new RDMLoginRequestAttrib();

/* RFA/Java only.
 */
		request.setMessageType (RDMLoginRequest.MessageType.REQUEST);
		request.setIndicationMask (EnumSet.of (RDMLoginRequest.IndicationMask.REFRESH));
		attribInfo.setRole (RDMLogin.Role.CONSUMER);

/* DACS username (required).
 */
		attribInfo.setNameType (RDMLogin.NameType.USER_NAME);
		attribInfo.setName (this.config.hasUserName() ?
			this.config.getUserName()
			: System.getProperty ("user.name"));

/* DACS Application Id (optional).
 * e.g. "256"
 */
		if (this.config.hasApplicationId())
			attribInfo.setApplicationId (this.config.getApplicationId());

/* DACS Position name (optional).
 * e.g. "localhost"
 */
		if (this.config.hasPosition()) {
			if (!this.config.getPosition().isEmpty())
				attribInfo.setPosition (this.config.getPosition());
		} else {
			StringBuilder position = new StringBuilder (InetAddress.getLocalHost().getHostAddress());
			position.append ('/');
			position.append (InetAddress.getLocalHost().getHostName());
			attribInfo.setPosition (position.toString());
		}

/* Instance Id (optional).
 * e.g. "<Instance Id>"
 */
		if (this.config.hasInstanceId())
			attribInfo.setInstanceId (this.config.getInstanceId());

		request.setAttrib (attribInfo);

		LOG.trace ("Registering OMM item interest for MMT_LOGIN.");
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
		LOG.trace ("Sending directory request.");
		RDMDirectoryRequest request = new RDMDirectoryRequest();
		RDMDirectoryRequestAttrib attribInfo = new RDMDirectoryRequestAttrib();

/* RFA/Java only.
 */
		request.setMessageType (RDMDirectoryRequest.MessageType.REQUEST);
		request.setIndicationMask (EnumSet.of (RDMDirectoryRequest.IndicationMask.REFRESH));

/* Limit to named service */
		if (this.config.hasServiceName())
			attribInfo.setServiceName (this.config.getServiceName());

/* Only request INFO filters */
		attribInfo.setFilterMask (EnumSet.of (RDMDirectory.FilterMask.INFO));

		request.setAttrib (attribInfo);

		LOG.trace ("Registering OMM item interest for MMT_DIRECTORY.");
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
	private void sendDictionaryRequest (String service_name, String dictionary_name) {
		LOG.trace ("Sending dictionary request for \"{}\".", dictionary_name);
		RDMDictionaryRequest request = new RDMDictionaryRequest();
		RDMDictionaryRequestAttrib attribInfo = new RDMDictionaryRequestAttrib();

/* RFA/Java only.
 */
		request.setMessageType (RDMDictionaryRequest.MessageType.REQUEST);
		request.setIndicationMask (EnumSet.of (RDMDictionaryRequest.IndicationMask.REFRESH));

// RDMDictionary.Filter.NORMAL=0x7: Provides all information needed for decoding
		attribInfo.setVerbosity (RDMDictionary.Verbosity.NORMAL);
		attribInfo.setDictionaryName (dictionary_name);
		attribInfo.setServiceName (service_name);

		request.setAttrib (attribInfo);

		LOG.trace ("Registering OMM item interest for MMT_DICTIONARY/{}.", dictionary_name);
		OMMMsg msg = request.getMsg (this.omm_pool);
		OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
		ommItemIntSpec.setMsg (msg);
		this.dictionary_handle.put (dictionary_name,
			new FlaggedHandle (this.omm_consumer.registerClient (this.event_queue, ommItemIntSpec, this, dictionary_name /* closure */)));
	}

	@Override
	public void processEvent (Event event) {
		LOG.trace (event);
		switch (event.getType()) {
		case Event.OMM_ITEM_EVENT:
			this.OnOMMItemEvent ((OMMItemEvent)event);
			break;

		case Event.OMM_CONNECTION_EVENT:
			this.OnConnectionEvent ((OMMConnectionEvent)event);
			break;

		case Event.MARKET_DATA_ITEM_EVENT:
			this.OnMarketDataItemEvent ((MarketDataItemEvent)event);
			break;

		case Event.MARKET_DATA_SVC_EVENT:
			this.OnMarketDataSvcEvent ((MarketDataSvcEvent)event);
			break;

		case Event.CONNECTION_EVENT:
			this.OnConnectionEvent ((ConnectionEvent)event);
			break;

		case Event.ENTITLEMENTS_AUTHENTICATION_EVENT:
			this.OnEntitlementsAuthenticationEvent ((EntitlementsAuthenticationEvent)event);
			break;

		default:
			LOG.trace ("Uncaught: {}", event);
			break;
		}
	}

/* Handling Item Events, message types are munged c.f. C++ API.
 */
	private void OnOMMItemEvent (OMMItemEvent event) {
		LOG.trace ("OnOMMItemEvent: {}", event);
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
			LOG.trace ("Uncaught: {}", msg);
			break;
		}
	}

	private void OnRespMsg (OMMMsg msg, Handle handle, Object closure) {
		LOG.trace ("OnRespMsg: {}", msg);
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
			LOG.trace ("Uncaught: {}", msg);
			break;
		}
	}

	private void OnLoginResponse (OMMMsg msg) {
		LOG.trace ("OnLoginResponse: {}", msg);
//GenericOMMParser.parse (msg);
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
				LOG.trace ("Uncaught data state: {}", response);
				break;
			}
			break;

		case OMMState.Stream.CLOSED:
			this.OnLoginClosed (response);
			break;

		default:
			LOG.trace ("Uncaught stream state: {}", response);
			break;
		}
	}

/* Login Success.
 */
	private void OnLoginSuccess (RDMLoginResponse response) {
		LOG.trace ("OnLoginSuccess: {}", response);
		LOG.trace ("Unmuting consumer.");
		this.is_muted = false;
	}

/* Other Login States.
 */
	private void OnLoginSuspect (RDMLoginResponse response) {
		LOG.trace ("OnLoginSuspect: {}", response);
		this.is_muted = true;
	}

/* Other Login States.
 */
	private void OnLoginClosed (RDMLoginResponse response) {
		LOG.trace ("OnLoginClosed: {}", response);
		this.is_muted = true;
	}

/* MMT_DIRECTORY domain.
 */
	private void OnDirectoryResponse (OMMMsg msg) {
		LOG.trace ("OnDirectoryResponse: {}", msg);
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
					LOG.trace ("Service: {}", service.getServiceName());
				}

				this.pending_directory = false;
			}
		} */

/* WORKAROUND: request each listed "used" dictionaries.  Assumes that the provider
 * or connected infrastructure will provide the dictionaries.
 */
		if ((OMMMsg.MsgType.REFRESH_RESP == msg.getMsgType()
			|| OMMMsg.MsgType.UPDATE_RESP == msg.getMsgType())
/* check for payload */
			&& OMMTypes.NO_DATA != msg.getDataType())
		{
			final OMMMap map = (OMMMap)msg.getPayload();
			for (Iterator<?> it = map.iterator(); it.hasNext();) {
				final OMMMapEntry map_entry = (OMMMapEntry)it.next();
				final OMMData map_key = map_entry.getKey();
				if (OMMTypes.FILTER_LIST != map_entry.getDataType()
					|| OMMTypes.ASCII_STRING != map_key.getType())
				{
					LOG.trace ("OMM map entry not a filter list.");
					continue;
				}
				final String service_name = ((OMMDataBuffer)map_key).toString();
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
					LOG.trace ("OMM filter list contains no INFO filter.");
					continue;
				}
				LOG.trace ("OMM filter list contains INFO filter.");
				if (null == info_filter || OMMTypes.ELEMENT_LIST != info_filter.getDataType()) {
					LOG.trace ("INFO filter is not an OMM element list.");
					continue;
				}
				final OMMElementList element_list = (OMMElementList)info_filter.getData();
				for (Iterator<?> jt = element_list.iterator(); jt.hasNext();) {
					final OMMElementEntry element_entry = (OMMElementEntry)jt.next();
					final OMMData element_data = element_entry.getData();
					if (!element_entry.getName().equals (com.reuters.rfa.rdm.RDMService.Info.DictionariesUsed))
						continue;
					LOG.trace ("Found DictionariesUsed entry.");
					if (OMMTypes.ARRAY != element_data.getType()) {
						LOG.trace ("DictionariesUsed not an OMM array.");
						continue;
					}
					final OMMArray array = (OMMArray)element_data;
					Iterator<?> kt = array.iterator();
					while (kt.hasNext()) {
						final OMMEntry array_entry = (OMMEntry)kt.next();
						final String dictionary_name = array_entry.getData().toString();
						if (!this.dictionary_handle.containsKey (dictionary_name))
							this.sendDictionaryRequest (service_name, dictionary_name);
						LOG.trace ("Used dictionary: {}", dictionary_name);
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
		LOG.trace ("OnDictionaryResponse: {}", msg);
		final RDMDictionaryResponse response = new RDMDictionaryResponse (msg);
/* Receiving dictionary */
		if (response.getMessageType() == RDMDictionaryResponse.MessageType.REFRESH_RESP
			&& response.hasPayload() && null != response.getPayload())
		{
			if (response.hasAttrib()) {
				LOG.trace ("Dictionary: {}", response.getAttrib().getDictionaryName());
			}
			this.rdm_dictionary.load (response.getPayload(), handle);
		}

/* Only know type after it is loaded. */
		final RDMDictionary.DictionaryType dictionary_type = this.rdm_dictionary.getDictionaryType (handle);

/* Received complete dictionary */
		if (response.getMessageType() == RDMDictionaryResponse.MessageType.REFRESH_RESP
			&& response.getIndicationMask().contains (RDMDictionaryResponse.IndicationMask.REFRESH_COMPLETE))
		{
			LOG.trace ("Dictionary complete.");
/* Check dictionary version */
			FieldDictionary field_dictionary = this.rdm_dictionary.getFieldDictionary();
			if (RDMDictionary.DictionaryType.RWFFLD == dictionary_type)
			{
				LOG.trace ("RDMFieldDictionary version: {}", field_dictionary.getFieldProperty ("Version"));
			}
			else if (RDMDictionary.DictionaryType.RWFENUM == dictionary_type)
			{
				LOG.trace ("enumtype.def version: {}", field_dictionary.getEnumProperty ("Version"));
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
				LOG.trace ("All used dictionaries loaded, resuming subscriptions.");
				this.resubscribe();
				this.pending_dictionary = false;
			} else {
				LOG.trace ("Dictionaries pending: {}", pending_dictionaries);
			}
		}
	}

/* MMT_MARKETPRICE domain.
 */
	private void OnMarketPrice (OMMMsg msg) {
		GenericOMMParser.parse (msg);
	}


	private void OnConnectionEvent (OMMConnectionEvent event) {
		LOG.trace ("OnConnectionEvent: {}", event);
	}

	private void OnMarketDataItemEvent (MarketDataItemEvent event) {
		final DateTime dt = new DateTime();
		LOG.trace ("OnMarketDataItemEvent: {}", event);
/* strings in switch are not supported in -source 1.6 */
/* ignore initial images and refreshes */
		if (/* MarketDataItemEvent.IMAGE == event.getMarketDataMsgType()
			|| */ MarketDataItemEvent.UPDATE == event.getMarketDataMsgType()) {
		}
		else if (MarketDataItemEvent.UNSOLICITED_IMAGE == event.getMarketDataMsgType()) {
			LOG.trace ("Ignoring unsolicited image.");
			return;
		}
		else if (MarketDataItemEvent.STATUS == event.getMarketDataMsgType()) {
			LOG.trace ("Status: {}", event);

/* MARKET_DATA_ITEM_EVENT, service = ELEKTRON_EDGE, item = RBK,
 * MarketDataMessageType = STATUS, MarketDataItemStatus = { state: CLOSED,
 * code: NONE, text: "The record could not be found"}, data = NULL
 */

/* Item stream recovered. */
			if (MarketDataItemStatus.OK == event.getStatus().getState())
				return;

/* ICAP error output here */
			final ItemStream item_stream = (ItemStream)event.getClosure();
			StringBuilder icap = new StringBuilder()
				.append (item_stream.getServiceName())
				.append (',')
				.append (item_stream.getItemName())
				.append (',')
				.append (dt.toString())
				.append (',');
/* Rewrite to RSSL/OMM semantics, (Stream,Data,Code)
 *
 * Examples: OPEN,OK,NONE
 * 	     - The item is served by the provider. The consumer application established
 * 	       the item event stream.
 *
 * 	     OPEN,SUSPECT,NO_RESOURCES
 * 	     - The provider does not offer data for the requested item at this time.
 * 	       However, the system will try to recover this item when available.
 *
 * 	     CLOSED_RECOVER,SUSPECT,NO_RESOURCES
 * 	     - The provider does not offer data for the requested item at this time. The
 * 	       application can try to re-request the item later.
 *
 * 	     CLOSED,SUSPECT,/any/
 * 	     -  The item is not open on the provider, and the application should close this
 * 	        stream.
 */
			String stream_state = "OPEN", data_state = "NO_CHANGE";
			if (event.isEventStreamClosed()
				|| MarketDataItemStatus.CLOSED == event.getStatus().getState())
			{
				stream_state = "CLOSED";
				data_state = "SUSPECT";
			}
			else if (MarketDataItemStatus.CLOSED_RECOVER == event.getStatus().getState())
			{
				stream_state = "CLOSED_RECOVER";
				data_state = "SUSPECT";
			}
			else if (MarketDataItemStatus.STALE == event.getStatus().getState())
			{
				data_state = "SUSPECT";
			}

			icap	.append (stream_state)
				.append (',')
				.append (data_state)
				.append (',')
				.append (event.getStatus().getStatusCode().toString())
				.append (',')
				.append (event.getStatus().getStatusText());
			LOG.info (ICAP_MARKER, icap.toString());
			return;
		}
		else if (MarketDataItemEvent.CORRECTION == event.getMarketDataMsgType()) {
			LOG.trace ("Ignoring correction.");
			return;
		}
		else if (MarketDataItemEvent.CLOSING_RUN == event.getMarketDataMsgType()) {
			LOG.trace ("Ignoring closing run.");
			return;
		}
		else if (MarketDataItemEvent.RENAME == event.getMarketDataMsgType()) {
			LOG.trace ("Ignoring rename.");
			return;
		}
		else if (MarketDataItemEvent.PERMISSION_DATA == event.getMarketDataMsgType()) {
			LOG.trace ("Ignoring permission data.");
			return;
		}
/* GROUP_CHANGE is deprecated */
		else {
			LOG.trace ("Unhandled market data message type ({}).", event.getMarketDataMsgType());
			return;
		}

		if (MarketDataEnums.DataFormat.MARKETFEED != event.getDataFormat()) {
			StringBuilder format = new StringBuilder();
			switch (event.getDataFormat()) {
			case MarketDataEnums.DataFormat.UNKNOWN:
				format.append ("Unknown");
				break;
			case MarketDataEnums.DataFormat.ANSI_PAGE:
				format.append ("ANSI_Page");
				break;
			case MarketDataEnums.DataFormat.MARKETFEED:
				format.append ("Marketfeed");
				break;
			case MarketDataEnums.DataFormat.QFORM:
				format.append ("QForm");
				break;
/* TibMsg self-describing */
			case MarketDataEnums.DataFormat.TIBMSG:
				format.append ("TibMsg");
				break;
			case MarketDataEnums.DataFormat.IFORM:
			default:
				format.append (event.getDataFormat());
				break;
			}

			LOG.trace ("Unsupported data format ({}) in market data item event.", format.toString());
			return;
		}

		final byte[] data = event.getData();
		final int length = (data != null ? data.length : 0);

		if (0 == length)
			return;

		try {
			this.msg.UnPack (data);
			if (LOG.isDebugEnabled()) {
				for (int status = this.field.First (msg);
					TibMsg.TIBMSG_OK == status;
					status = this.field.Next())
				{
					LOG.debug (new StringBuilder()
						.append (this.field.Name())
						.append (": ")
						.append (this.field.StringData())
						.toString());
				}
			}

/* ICAP output here */
			final ItemStream item_stream = (ItemStream)event.getClosure();
			StringBuilder icap = new StringBuilder()
				.append (item_stream.getServiceName())
				.append (',')
				.append (item_stream.getItemName())
				.append (',')
				.append (dt.toString());
			for (String field : item_stream.getView()) {
				icap.append (',')
					.append (this.msg.Get (field).StringData());
			}
			LOG.info (ICAP_MARKER, icap.toString());

		} catch (TibException e) {
			LOG.warn ("Unable to unpack data with TibMsg: {}", e.getMessage());
		}
	}

	private void OnMarketDataSvcEvent (MarketDataSvcEvent event) {
		LOG.trace ("OnMarketDataSvcEvent: {}", event);
/* Wait for any service to be up instead of one named service */
		if (/* event.getServiceName().equals (this.config.getServiceName())
			&& */ MarketDataSvcStatus.UP == event.getStatus().getState()
			&& this.is_muted)
		{
			this.is_muted = false;
			this.resubscribe();
		}
	}

	private void OnConnectionEvent (ConnectionEvent event) {
		LOG.trace ("OnConnectionEvent: {}", event);
	}

	private void OnEntitlementsAuthenticationEvent (EntitlementsAuthenticationEvent event) {
		LOG.trace ("OnEntitlementsAuthenticationEvent: {}", event);
	}
}

/* eof */
