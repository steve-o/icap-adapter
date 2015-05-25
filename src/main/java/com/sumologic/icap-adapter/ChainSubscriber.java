/* Chain subscriber.
 */

package com.sumologic.IcapAdapter;

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

public class ChainSubscriber implements Handle {
	private static Logger LOG = LogManager.getLogger (ChainSubscriber.class.getName());

	private final MarketDataSubscriber market_data_subscriber;
	private final EventQueue event_queue;
	private final MarketDataItemSub marketDataItemSub;
	private final ChainListener listener;
	private final java.lang.Object closure;
	private final LinkSubscriber link_subscriber;
	private final Set<String> all_links;
	private final Multiset<String> all_items;

	ChainSubscriber (MarketDataSubscriber aSubscriber, TibMsg msg, TibField field, EventQueue aQueue, MarketDataItemSub aSubscription, ChainListener listener, java.lang.Object aClosure) {
		this.market_data_subscriber = aSubscriber;
		this.event_queue = aQueue;
		this.marketDataItemSub = aSubscription;
		this.listener = listener;
		this.closure = aClosure;

/* set of all link names to prevent cyclic subscriptions */
		this.all_links = Sets.newHashSet();
		this.link_subscriber = new LinkSubscriber (this, msg, field, this.marketDataItemSub.getItemName());
/* superset of item names */
		this.all_items = HashMultiset.create();
	}

	public void Clear() {
		this.link_subscriber.Clear();
	}

	@Override
	public boolean isActive() {
		return true;
	}

/* Returns null on cyclic subscription */
	public Handle SubscribeLink (String link_name, Client client) {
		if (this.all_links.contains (link_name)) {
			LOG.warn ("Ignoring cyclic link subscription {}", link_name);
			return null;
		} else {
			LOG.trace ("Subscribing to chain link {}", link_name);
			this.AddLink (link_name);
			this.marketDataItemSub.setItemName (link_name);
			return this.market_data_subscriber.subscribe (this.event_queue, this.marketDataItemSub, client, null);
		}
	}

	public void UnsubscribeLink (String link_name, Handle handle) {
		LOG.trace ("Unsubscribing to chain link {}", link_name);
		this.market_data_subscriber.unsubscribe (handle);
		this.all_links.remove (link_name);
	}

/* Explicit control of link list: note race conditions on adding and removing.
 * Link must be added before subscribe but not before cyclic check.
 */
	public void AddLink (String link_name) {
		this.all_links.add (link_name);
	}

	public void RemoveLink (String link_name) {
		this.all_links.remove (link_name);
	}

/* Can propagate fairly useless values like the empty string "" */
	public void OnAddEntry (String item_name) {
		if (!this.all_items.contains (item_name)) {
			this.listener.OnAddEntry (item_name, this.closure);
		}
		this.all_items.add (item_name);
	}

	public void OnUpdateEntry (String old_name, String new_name) {
		this.OnRemoveEntry (old_name);
		this.OnAddEntry (new_name);
	}

	public void OnRemoveEntry (String item_name) {
		this.all_items.remove (item_name);
		if (!this.all_items.contains (item_name)) {
			this.listener.OnRemoveEntry (item_name, this.closure);
		}
	}
}

/* eof */
