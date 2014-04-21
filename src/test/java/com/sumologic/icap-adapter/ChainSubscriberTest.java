/*
 */

package com.sumologic.IcapAdapter;

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

import java.util.List;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ChainSubscriberTest extends TestCase {

	@Mock private MarketDataSubscriber subscriber;
	@Mock private EventQueue queue;
	@Mock private ChainListener listener;
	@Spy private TibMsg msg;
	private TibField field;
	private MarketDataItemSub subscription;
	private java.lang.Object closure;

	private ChainSubscriber chain;

	@Before
	public void setUp() throws Exception {
		this.field = new TibField();
		this.subscription = new MarketDataItemSub();
		this.closure = null;
/* preload chain dictionary */
		Chains.NEXT_LR_FID = 238;
		Chains.REF_COUNT_FID = 239;
		Chains.LINK_1_FID = 240;
		Chains.LINK_14_FID = 253;
		Chains.LONGLINK1_FID = 800;
		Chains.LONGLINK14_FID = 813;
		Chains.LONGNEXTLR_FID = 815;
	}

	@Test
	public void Recycle() {
		this.subscription.setItemName ("0#.FTSE");
/* subscription handle */
		Handle handle = mock (Handle.class);
		when (this.subscriber.subscribe (eq (this.queue), eq (this.subscription), any (Client.class), any()))
			.thenReturn (handle);
/* subscription */
		this.chain = new ChainSubscriber (this.subscriber, this.msg, this.field, this.queue, this.subscription, this.listener, this.closure);
/* close out */
		this.chain.Clear();
		verify (this.subscriber).subscribe (eq (this.queue), eq (this.subscription), any (Client.class), any());
		verify (this.subscriber).unsubscribe (handle);
		verifyNoMoreInteractions (this.subscriber);
	}

	@Test
	public void ItemReorder() throws TibException {
		this.subscription.setItemName ("0#.FTSE");
/* subscription handle */
		Handle handle = mock (Handle.class);
		when (this.subscriber.subscribe (eq (this.queue), eq (this.subscription), any (Client.class), any()))
			.thenReturn (handle);
/* subscription */
		this.chain = new ChainSubscriber (this.subscriber, this.msg, this.field, this.queue, this.subscription, this.listener, this.closure);
		ArgumentCaptor<Client> client = ArgumentCaptor.forClass (Client.class);
		verify (this.subscriber).subscribe (eq (this.queue), eq (this.subscription), client.capture(), any());
/* refresh image */
		MarketDataItemEvent event = mock (MarketDataItemEvent.class);
		when (event.isEventStreamClosed()).thenReturn (false);
		when (event.getType()).thenReturn (Event.MARKET_DATA_ITEM_EVENT);
		when (event.getMarketDataMsgType()).thenReturn (MarketDataItemEvent.IMAGE);
		this.msg.ReUse();
		TibField field = new TibField ("LINK_1", "TIBX.O");
		field.SetMfeedFid (240);
		this.msg.Append (field);
		byte[] data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
		ArgumentCaptor<String> string = ArgumentCaptor.forClass (String.class);
		verify (this.listener).OnAddEntry (string.capture(), eq (this.closure));
		assertEquals ("TIBX.O", string.getValue());
/* update image: move TIBCO to #2, Google to #1 */
		reset (event);
		reset (this.listener);
		when (event.isEventStreamClosed()).thenReturn (false);
		when (event.getType()).thenReturn (Event.MARKET_DATA_ITEM_EVENT);
		when (event.getMarketDataMsgType()).thenReturn (MarketDataItemEvent.UPDATE);
		this.msg.ReUse();
		field = new TibField ("LINK_1", "GOOGL.O");
		field.SetMfeedFid (240);
		this.msg.Append (field);
		field = new TibField ("LINK_2", "TIBX.O");
		field.SetMfeedFid (241);
		this.msg.Append (field);
		data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
		InOrder inOrder = inOrder (this.listener);
		string = ArgumentCaptor.forClass (String.class);
		inOrder.verify (this.listener).OnRemoveEntry (string.capture(), eq (this.closure));
		assertEquals ("TIBX.O", string.getValue());
		string = ArgumentCaptor.forClass (String.class);
		inOrder.verify (this.listener, times (2)).OnAddEntry (string.capture(), eq (this.closure));
		assertEquals ("GOOGL.O", string.getAllValues().get (0));
		assertEquals ("TIBX.O", string.getAllValues().get (1));
/* close out */
		this.chain.Clear();
		verify (this.subscriber).unsubscribe (handle);
		verifyNoMoreInteractions (this.subscriber);
	}

	@Test
	public void LinkResize() throws TibException {
		this.subscription.setItemName ("0#.FTSE");
/* one link */
		Handle handle0 = mock (Handle.class), handle1 = mock (Handle.class);
		when (this.subscriber.subscribe (eq (this.queue), any (MarketDataItemSub.class), any (Client.class), any()))
			.thenReturn (handle0, handle1);
		this.chain = new ChainSubscriber (this.subscriber, this.msg, this.field, this.queue, this.subscription, this.listener, this.closure);
		ArgumentCaptor<MarketDataItemSub> subscription0 = ArgumentCaptor.forClass (MarketDataItemSub.class);
		ArgumentCaptor<Client> client0 = ArgumentCaptor.forClass (Client.class);
		verify (this.subscriber).subscribe (eq (this.queue), subscription0.capture(), client0.capture(), any());
		assertEquals ("0#.FTSE", subscription0.getValue().getItemName());
		MarketDataItemEvent event = mock (MarketDataItemEvent.class);
		when (event.isEventStreamClosed()).thenReturn (false);
		when (event.getType()).thenReturn (Event.MARKET_DATA_ITEM_EVENT);
		when (event.getMarketDataMsgType()).thenReturn (MarketDataItemEvent.IMAGE);
		this.msg.ReUse();
		TibField field = new TibField ("NEXT_LR", "");
		field.SetMfeedFid (238);
		this.msg.Append (field);
		byte[] data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client0.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
/* two links */
		reset (event);
		when (event.isEventStreamClosed()).thenReturn (false);
		when (event.getType()).thenReturn (Event.MARKET_DATA_ITEM_EVENT);
		when (event.getMarketDataMsgType()).thenReturn (MarketDataItemEvent.UPDATE);
		field = new TibField ("NEXT_LR", "1#.FTSE");
		field.SetMfeedFid (238);
		this.msg.Update (field);
		data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client0.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
		ArgumentCaptor<MarketDataItemSub> subscription1 = ArgumentCaptor.forClass (MarketDataItemSub.class);
		ArgumentCaptor<Client> client1 = ArgumentCaptor.forClass (Client.class);
		verify (this.subscriber, times (2)).subscribe (eq (this.queue), subscription1.capture(), client1.capture(), any());
		assertEquals ("1#.FTSE", subscription1.getValue().getItemName());
		field = new TibField ("NEXT_LR", "");
		field.SetMfeedFid (238);
		this.msg.Update (field);
		data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client1.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
/* one link */
		reset (this.subscriber);
		client0.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
		verify (this.subscriber).unsubscribe (handle1);
/* close out */
		reset (this.subscriber);
		this.chain.Clear();
		verify (this.subscriber).unsubscribe (handle0);
		verifyNoMoreInteractions (this.subscriber);
	}

	@Test
	public void CyclicLink() throws TibException {
		this.subscription.setItemName ("0#.FTSE");
/* one link */
		Handle handle0 = mock (Handle.class), handle1 = mock (Handle.class);
		when (this.subscriber.subscribe (eq (this.queue), any (MarketDataItemSub.class), any (Client.class), any()))
			.thenReturn (handle0, handle1);
		this.chain = new ChainSubscriber (this.subscriber, this.msg, this.field, this.queue, this.subscription, this.listener, this.closure);
		ArgumentCaptor<MarketDataItemSub> subscription0 = ArgumentCaptor.forClass (MarketDataItemSub.class);
		ArgumentCaptor<Client> client0 = ArgumentCaptor.forClass (Client.class);
		verify (this.subscriber).subscribe (eq (this.queue), subscription0.capture(), client0.capture(), any());
		assertEquals ("0#.FTSE", subscription0.getValue().getItemName());
		MarketDataItemEvent event = mock (MarketDataItemEvent.class);
		when (event.isEventStreamClosed()).thenReturn (false);
		when (event.getType()).thenReturn (Event.MARKET_DATA_ITEM_EVENT);
		when (event.getMarketDataMsgType()).thenReturn (MarketDataItemEvent.IMAGE);
		this.msg.ReUse();
		TibField field = new TibField ("NEXT_LR", "1#.FTSE");
		field.SetMfeedFid (238);
		this.msg.Append (field);
		byte[] data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client0.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
		ArgumentCaptor<MarketDataItemSub> subscription1 = ArgumentCaptor.forClass (MarketDataItemSub.class);
		ArgumentCaptor<Client> client1 = ArgumentCaptor.forClass (Client.class);
		verify (this.subscriber, times (2)).subscribe (eq (this.queue), subscription1.capture(), client1.capture(), any());
		assertEquals ("1#.FTSE", subscription1.getValue().getItemName());
		field = new TibField ("NEXT_LR", "0#.FTSE");
		field.SetMfeedFid (238);
		this.msg.Update (field);
		data = this.msg.Packed();
		when (event.getData()).thenReturn (data);
		client1.getValue().processEvent (event);
		verify (event, atLeastOnce()).getType();
		verify (event, atLeastOnce()).getMarketDataMsgType();
		verifyNoMoreInteractions (this.subscriber);
/* close out */
		reset (this.subscriber);
		this.chain.Clear();
		verify (this.subscriber).unsubscribe (handle1);
		verify (this.subscriber).unsubscribe (handle0);
		verifyNoMoreInteractions (this.subscriber);
	}
}

/* eof */
