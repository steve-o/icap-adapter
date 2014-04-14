/* Chain subscriber.
 */

package com.sumologic.IcapAdapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Optional;
import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.event.MarketDataItemEvent;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.tibmsg.TibException;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

public class ChainSubscriber implements Handle, Client {
	private static Logger LOG = LogManager.getLogger (ChainSubscriber.class.getName());

	private final MarketDataSubscriber market_data_subscriber;
	private final EventQueue event_queue;
	private final MarketDataItemSub marketDataItemSub;
	private final ChainListener listener;
	private final java.lang.Object closure;
	private final Map<String, Handle> link_handles;
	private final Set<String> items;

	private TibMsg msg;
	private TibField field;

	private static final int MAX_ITEMS_IN_LINK = 14;

	private static final int PREV_LR_FID		= 237;	/* Previous link in chain */
	private static final int NEXT_LR_FID		= 238;	/* Next link in chain */
	private static final int REF_COUNT_FID		= 239;	/* Count of valid items in this link */
	private static final int LINK_1_FID		= 240;
	private static final int LINK_2_FID		= 241;
	private static final int LINK_3_FID		= 242;
	private static final int LINK_4_FID		= 243;
	private static final int LINK_5_FID		= 244;
	private static final int LINK_6_FID		= 245;
	private static final int LINK_7_FID		= 246;
	private static final int LINK_8_FID		= 247;
	private static final int LINK_9_FID		= 248;
	private static final int LINK_10_FID		= 249;
	private static final int LINK_11_FID		= 250;
	private static final int LINK_12_FID		= 251;
	private static final int LINK_13_FID		= 252;
	private static final int LINK_14_FID		= 253;
	private static final int LONGLINK_1_FID		= 800;
	private static final int LONGLINK_2_FID		= 801;
	private static final int LONGLINK_3_FID		= 802;
	private static final int LONGLINK_4_FID		= 803;
	private static final int LONGLINK_5_FID		= 804;
	private static final int LONGLINK_6_FID		= 805;
	private static final int LONGLINK_7_FID		= 806;
	private static final int LONGLINK_8_FID		= 807;
	private static final int LONGLINK_9_FID		= 808;
	private static final int LONGLINK_10_FID	= 809;
	private static final int LONGLINK_11_FID	= 810;
	private static final int LONGLINK_12_FID	= 811;
	private static final int LONGLINK_13_FID	= 812;
	private static final int LONGLINK_14_FID	= 813;
	private static final int LONGPREVLR_FID		= 814;
	private static final int LONGNEXTLR_FID		= 815;

	ChainSubscriber (MarketDataSubscriber aSubscriber, EventQueue aQueue, MarketDataItemSub aSubscription, ChainListener listener, java.lang.Object aClosure) {
		this.market_data_subscriber = aSubscriber;
		this.event_queue = aQueue;
		this.marketDataItemSub = aSubscription;
		this.listener = listener;
		this.closure = aClosure;

		this.link_handles = new HashMap<String, Handle>();
		this.link_handles.put (aSubscription.getItemName(), this.subscribeLink (aSubscription.getItemName()));

		this.items = new HashSet<String>();

		this.msg = new TibMsg();
		this.field = new TibField();
	}

	@Override
	public boolean isActive() {
		return true;
	}

	private Handle subscribeLink (String link_name) {
		LOG.trace ("Subscribing to link {}", link_name);
		this.marketDataItemSub.setItemName (link_name);
		return this.market_data_subscriber.subscribe (this.event_queue, this.marketDataItemSub, this, null);
	}

	@Override
	public void processEvent (Event event) {
		LOG.trace (event);
		switch (event.getType()) {
		case Event.MARKET_DATA_ITEM_EVENT:
			this.OnMarketDataItemEvent ((MarketDataItemEvent)event);
			break;

		default:
			LOG.trace ("Uncaught: {}", event);
			break;
		}
	}

	private void OnMarketDataItemEvent (MarketDataItemEvent event) {
		LOG.trace ("OnMarketDataItemEvent: {}", event);
/* Remove this link pending future resubmission to the chain. */
		if (event.isEventStreamClosed()) {
			LOG.trace ("Removing closed link subscription for \"{}\".", event.getItemName());
			this.link_handles.remove (event.getItemName());
/* 5.1.5.6 Close Event Stream
 * It is important to note that if the Event Stream had been closed by RFA,
 * the application must not call unsubscribe().
 * The Event Stream is already closed and the Handle is no longer valid.
 */
		}
/* Note message types CORRECT, CLOSING_RUN are ignored.
 */
		if (MarketDataItemEvent.UPDATE != event.getMarketDataMsgType() &&
			MarketDataItemEvent.IMAGE != event.getMarketDataMsgType() &&
			MarketDataItemEvent.UNSOLICITED_IMAGE != event.getMarketDataMsgType())
		{
			return;
		}

		final byte[] data = event.getData();
		final int length = (data != null ? data.length : 0);
		if (0 == length) return;

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

/* Detect if chain link and evalute required FIDs as discovered */
			Optional<Integer> ref_count = Optional.absent();
			Optional<String> prev_lr = Optional.absent(), next_lr = Optional.absent();
			Optional<String>[] links = new Optional[MAX_ITEMS_IN_LINK];
			Arrays.fill (links, Optional.absent());
			for (int status = this.field.First (msg);
				TibMsg.TIBMSG_OK == status;
				status = this.field.Next())
			{
				final int fid = this.field.MfeedFid();
				if (REF_COUNT_FID == fid) {
					ref_count = Optional.of (new Integer (this.field.IntData()));
				} else if (PREV_LR_FID == fid || LONGPREVLR_FID == fid) {
					prev_lr = Optional.of (this.field.StringData());
				} else if (NEXT_LR_FID == fid || LONGNEXTLR_FID == fid) {
					next_lr = Optional.of (this.field.StringData());
				} else if (fid >= LINK_1_FID && fid <= LINK_14_FID) {
					final int i = fid - LINK_1_FID;
					links[i] = Optional.of (this.field.StringData());
				} else if (fid >= LONGLINK_1_FID && fid <= LONGLINK_14_FID) {
					final int i = fid - LONGLINK_1_FID;
					links[i] = Optional.of (this.field.StringData());
				}
			}

/* Validate chain fields */
			int link_count = ref_count.isPresent() ? ref_count.get().intValue() : links.length;
			if (link_count > links.length || link_count < 0) {
				LOG.trace ("REF_COUNT field has unexpected value {}.", ref_count.get());
				return;
			}
/* Record new items */
			for (int i = link_count; i > 0; --i) {
				if (!links[i - 1].isPresent()) {
					continue;
				}
				final String item_name = links[i - 1].get();
				if (this.items.contains (item_name)) {
					continue;
				} else {
					this.items.add (item_name);
					this.listener.OnAddEntry (item_name, this.closure);
				}
			}

/* Subscribe to next link */
			if (next_lr.isPresent() &&
				Chains.isChainLink (next_lr.get()) &&
				!this.link_handles.containsKey (next_lr.get()))
			{
				this.link_handles.put (next_lr.get(), this.subscribeLink (next_lr.get()));
			}

		} catch (TibException e) {
			LOG.trace ("Unable to unpack data with TibMsg: {}", e.getMessage());
		}
	}
}

/* eof */
