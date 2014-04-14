/* Chains static utility methods.
 */

package com.sumologic.IcapAdapter;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;

public final class Chains {

/* Assumptions:
 * 1) Must be first link of chain.
 * 2) Chain must have a link number, e.g. 0#.FTSE.
 */
	static boolean isChain (String item_name) {
		return (item_name.startsWith ("0#"));
	}

	static boolean isChainLink (String item_name) {
		return (item_name.matches ("^\\d+#.+$"));
	}

	static Handle subscribe (MarketDataSubscriber aSubscriber, EventQueue aQueue, MarketDataItemSub aSubscription, ChainListener listener, java.lang.Object aClosure) {
		Handle subscriber = new ChainSubscriber (aSubscriber, aQueue, aSubscription, listener, aClosure);
		return subscriber;
	}
}

/* eof */
