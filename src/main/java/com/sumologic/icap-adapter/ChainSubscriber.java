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
			Optional<String>[] links = new Optional[14];
			Arrays.fill (links, Optional.absent());
			for (int status = this.field.First (msg);
				TibMsg.TIBMSG_OK == status;
				status = this.field.Next())
			{
				final int fid = this.field.MfeedFid();
				if (239 == fid) {
					ref_count = Optional.of (new Integer (this.field.IntData()));
				} else if (237 == fid || 814 == fid) {
					prev_lr = Optional.of (this.field.StringData());
				} else if (238 == fid || 815 == fid) {
					next_lr = Optional.of (this.field.StringData());
				} else if (fid >= 240 && fid <= 253) {
					final int i = fid - 240;
					links[i] = Optional.of (this.field.StringData());
				} else if (fid >= 800 && fid <= 813) {
					final int i = fid - 800;
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
