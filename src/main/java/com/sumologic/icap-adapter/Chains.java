/* Chains static utility methods.
 */

package com.sumologic.IcapAdapter;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

public final class Chains {

/* Assumptions:
 * 1) Must be first link of chain.
 * 2) Chain must have a link number, e.g. 0#.FTSE.
 */
	static boolean isChain (String item_name) {
		return item_name.startsWith ("0#") ||
			item_name.startsWith (".AV.") ||	// active volume
			item_name.startsWith (".NG.") ||	// net gain
			item_name.startsWith (".NL.") ||	// net loss
			item_name.startsWith (".PG.") ||	// percent gain
			item_name.startsWith (".PL.");		// percent loss
	}

	static boolean isChainLink (String item_name) {
		return item_name.matches ("^\\d+#.+$");
	}

	static Handle subscribe (MarketDataSubscriber aSubscriber, TibMsg msg, TibField field, EventQueue aQueue, MarketDataItemSub aSubscription, ChainListener listener, java.lang.Object aClosure) {
		Handle subscriber = new ChainSubscriber (aSubscriber, msg, field, aQueue, aSubscription, listener, aClosure);
		return subscriber;
	}
}

/* eof */
