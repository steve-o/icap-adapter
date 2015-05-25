/* Chain link subscriber.
 */

package com.sumologic.IcapAdapter;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
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

public class LinkSubscriber implements Client {
	private static Logger LOG = LogManager.getLogger (LinkSubscriber.class.getName());

	private final ChainSubscriber chain;
	private final TibMsg msg;
	private final TibField field;
	private final String name;

	private final Optional<String>[] items;
	private int ref_count;
	private Optional<Handle> handle;
	private Optional<String> next_lr;
	private Optional<LinkSubscriber> next_link_subscriber;

	private static final int MAX_ITEMS_IN_LINK = 14;

	@SuppressWarnings("unchecked")
	LinkSubscriber (ChainSubscriber chain, TibMsg msg, TibField field, String name) {
		this.chain = chain;
		this.msg = msg;
		this.field = field;
		this.name = name;

/* each link in chain */
		this.items = new Optional[MAX_ITEMS_IN_LINK];
		Arrays.fill (this.items, Optional.absent());

/* count of valid items in link */
		this.ref_count = this.items.length;
/* next record link */
		this.next_lr = Optional.absent();
		this.next_link_subscriber = Optional.absent();

/* market data subscription, may return an absent handle but will update link state. */
		this.handle = Optional.fromNullable (this.chain.SubscribeLink (name, this));
	}

	public void Clear() {
		for (Optional<String> item_name : this.items) {
			if (item_name.isPresent()) {
				this.chain.OnRemoveEntry (item_name.get());
			}
		}
		Arrays.fill (this.items, Optional.absent());
/* Active subscription to link name is maintained independent of interest in the link. */
		if (this.handle.isPresent()) {
			this.chain.UnsubscribeLink (this.name, this.handle.get());
			this.handle = Optional.absent();
		}
		this.chain.RemoveLink (this.name);
		if (this.next_lr.isPresent()) {
			this.next_lr = Optional.absent();
		}
		if (this.next_link_subscriber.isPresent()) {
			final LinkSubscriber next_link_subscriber = this.next_link_subscriber.get();
			this.next_link_subscriber = Optional.absent();
			next_link_subscriber.Clear();
		}
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
			LOG.trace ("Subscription handle for \"{}\" is closed.", event.getItemName());
			this.handle = Optional.absent();
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
				for (int status = this.field.First (this.msg);
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

			for (int status = this.field.First (this.msg);
				TibMsg.TIBMSG_OK == status;
				status = this.field.Next())
			{
				final int fid = this.field.MfeedFid();
				if (Chains.REF_COUNT_FID == fid) {
					this.OnRefCount (this.field.IntData());
				} else if (Chains.NEXT_LR_FID == fid || Chains.LONGNEXTLR_FID == fid) {
					this.OnNextLink (this.field.StringData());
				} else if (fid >= Chains.LINK_1_FID && fid <= Chains.LINK_14_FID) {
					this.OnDataRecordReference (fid - Chains.LINK_1_FID, this.field.StringData());
				} else if (fid >= Chains.LONGLINK1_FID && fid <= Chains.LONGLINK14_FID) {
					this.OnDataRecordReference (fid - Chains.LONGLINK1_FID, this.field.StringData());
				}
			}
		} catch (TibException e) {
			LOG.trace ("Unable to unpack data with TibMsg: {}", e.getMessage());
		}
	}

/* TBD: Validate or invalidate a subset of items in this link */
	private void OnRefCount (int ref_count) {
		this.ref_count = ref_count;
	}

/* Only process next-link, previous-link more of an aid for UIs */
	private void OnNextLink (String next_lr) {
		if (this.next_lr.isPresent()) {
			if (next_lr.equals (this.next_lr.get())) {
				this.OnRefreshNextLink (next_lr);
			} else if (next_lr.isEmpty()) {
				this.OnRemoveNextLink();
			} else {
				this.OnUpdateNextLink (next_lr);
			}
		} else if (!next_lr.isEmpty()) {
			this.OnNewNextLink (next_lr);
		} else {
/* empty update on empty value: nop */
		}
	}

	private void OnRefreshNextLink (String next_lr) {
/* nop */
	}

	private void OnNewNextLink (String next_lr) {
		LOG.trace ("Chain link new NEXT_LR \"{}\"", next_lr);
		this.next_lr = Optional.of (next_lr);
		this.next_link_subscriber = Optional.fromNullable (new LinkSubscriber (this.chain, this.msg, this.field, next_lr));
	}

	private void OnUpdateNextLink (String next_lr) {
		LOG.trace ("Chain link NEXT_LR updated \"{}\" -> \"{}\"", this.next_lr.get(), next_lr);
		this.OnRemoveNextLink();
		this.OnNewNextLink (next_lr);
	}

	private void OnRemoveNextLink() {
		LOG.trace ("Chain link removed NEXT_LR \"{}\"", this.next_lr.get());
		this.next_link_subscriber.get().Clear();
		this.next_link_subscriber = Optional.absent();
		this.next_lr = Optional.absent();
	}

/* Propagate item updates up including empty and duplicate entries.
/* idx is relocated to base 0...13 from names LINK_1...LINK_14.
 */
	private void OnDataRecordReference (int idx, String data_record) {
		if (!this.items[idx].isPresent()) {
			LOG.trace ("Adding data record \"{}\" from chain.", data_record);
			this.items[idx] = Optional.of (data_record);
			this.chain.OnAddEntry (data_record);
		} else {
			final String old_value = this.items[idx].get();
			if (old_value.equals (data_record)) {
/* refresh-entry: nop */
			} else {
				LOG.trace ("Updating data record \"{}\" with \"{}\" from chain.", old_value, data_record);
				this.items[idx] = Optional.of (data_record);
				this.chain.OnUpdateEntry (old_value, data_record);
			}
		}
	}
}

/* eof */
