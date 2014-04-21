/* Chains static utility methods.
 */

package com.sumologic.IcapAdapter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

public final class Chains {
	private static Logger LOG = LogManager.getLogger (Chains.class.getName());

	static int PREV_LR_FID;		/* Previous link in chain */
	static int NEXT_LR_FID;		/* Next link in chain */
	static int REF_COUNT_FID;	/* Count of valid items in this link */
	static int LINK_1_FID;		/* Links must be contiguous */
	static int LINK_14_FID;
	static int LONGLINK1_FID;
	static int LONGLINK14_FID;
	static int LONGPREVLR_FID;
	static int LONGNEXTLR_FID;

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
		return item_name.matches ("^\\d+#.+$") ||
			item_name.matches ("^\\d+\\.(AV|NG|NL|PG|PL)\\..+$");
	}

	static void ApplyFieldDictionary (ImmutableMap<String, Integer> appendix_a) {
		checkNotNull (appendix_a);
		final String[] required = {"PREV_LR", "NEXT_LR", "REF_COUNT", "LINK_1", "LINK_2", "LINK_3", "LINK_4", "LINK_5", "LINK_6", "LINK_7", "LINK_8", "LINK_9", "LINK_10", "LINK_11", "LINK_12", "LINK_13", "LINK_14", "LONGLINK1", "LONGLINK2", "LONGLINK3", "LONGLINK4", "LONGLINK5", "LONGLINK6", "LONGLINK7", "LONGLINK8", "LONGLINK9", "LONGLINK10", "LONGLINK11", "LONGLINK12", "LONGLINK13", "LONGLINK14", "LONGPREVLR", "LONGNEXTLR"};
		for (String field : required) {
			if (!appendix_a.containsKey (field)) {
				LOG.error ("Missing dictionary definition for field \"{}\"", field);
			}
		}
		for (int i = 1; i < 14; ++i) {
			if (appendix_a.get ("LINK_" + i) + 1 != appendix_a.get ("LINK_" + (i + 1))) {
				LOG.error ("LINK_{} and LINK_{} FIDs are non-contiguous.", i, i + 1);
			}
			if (appendix_a.get ("LONGLINK" + i) + 1 != appendix_a.get ("LONGLINK" + (i + 1))) {
				LOG.error ("LONGLINK{} and LONGLINK{} FIDs are non-contiguous.", i, i + 1);
			}
		}
		PREV_LR_FID	= appendix_a.get ("PREV_LR");
		NEXT_LR_FID	= appendix_a.get ("NEXT_LR");
		REF_COUNT_FID	= appendix_a.get ("REF_COUNT");
		LINK_1_FID	= appendix_a.get ("LINK_1");
		LINK_14_FID	= appendix_a.get ("LINK_14");
		LONGLINK1_FID	= appendix_a.get ("LONGLINK1");
		LONGLINK14_FID	= appendix_a.get ("LONGLINK14");
		LONGPREVLR_FID	= appendix_a.get ("LONGPREVLR");
		LONGNEXTLR_FID	= appendix_a.get ("LONGNEXTLR");
	}

	static Handle subscribe (MarketDataSubscriber aSubscriber, TibMsg msg, TibField field, EventQueue aQueue, MarketDataItemSub aSubscription, ChainListener listener, java.lang.Object aClosure) {
		Handle subscriber = new ChainSubscriber (aSubscriber, msg, field, aQueue, aSubscription, listener, aClosure);
		return subscriber;
	}
}

/* eof */
