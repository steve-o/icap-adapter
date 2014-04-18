/*
 */

package com.sumologic.IcapAdapter;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ChainSubscriberTest extends TestCase {

	@Mock private MarketDataSubscriber subscriber;
	@Mock private EventQueue queue;
	@Mock private ChainListener listener;
	private TibMsg msg;
	private TibField field;
	private MarketDataItemSub subscription;
	private java.lang.Object closure;

	private ChainSubscriber chain;

	@Before
	public void setUp() throws Exception {
		this.msg = new TibMsg();
		this.field = new TibField();
		this.subscription = new MarketDataItemSub();
		this.closure = null;
	}

	@Test
	public void recycle() {
		this.subscription.setItemName ("0#.FTSE");
		Handle handle = mock (Handle.class);
		when (this.subscriber.subscribe (
			eq (this.queue),
			eq (this.subscription),
			any (Client.class),
			eq (null))).thenReturn (handle);
		this.chain = new ChainSubscriber (subscriber, msg, field, queue, subscription, listener, closure);
		this.chain.Clear();
		verify (this.subscriber).unsubscribe (handle);
	}

}

/* eof */
