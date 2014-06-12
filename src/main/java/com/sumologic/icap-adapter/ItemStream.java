/* Item stream runtime.
 */

package com.sumologic.IcapAdapter;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;
import com.reuters.rfa.common.Handle;

public class ItemStream {
/* Fixed name for this stream. */
	private String item_name;

/* Service origin, e.g. IDN_RDF */
	private String service_name;

/* Owning chain, if any */
	private Optional<String> chain_name;

/* Pseudo-view parameter, an array of field names */
	private ImmutableSortedSet<String> view_by_name;
	private ImmutableSortedSet<Integer> view_by_fid;

/* Subscription handle which is valid from login success to login close. */
	private Handle item_handle;

	private int reference_count;
	private Handle timer_handle;

/* Performance counters */

	public ItemStream() {
		this.chain_name = Optional.absent();
		this.setItemHandle (null);
		this.reference_count = 1;
	}

	public String getItemName() {
		return this.item_name;
	}

	public void setItemName (String item_name) {
		this.item_name = item_name;
	}

	public String getServiceName() {
		return this.service_name;
	}

	public void setServiceName (String service_name) {
		this.service_name = service_name;
	}

	public boolean hasChainName() {
		return this.chain_name.isPresent();
	}

	public String getChainName() {
		return this.chain_name.get();
	}

	public void setChainName (String chain_name) {
		this.chain_name = Optional.of (chain_name);
	}

	public ImmutableSortedSet<String> getViewByName() {
		return this.view_by_name;
	}

	public void setViewByName (ImmutableSortedSet<String> view) {
		this.view_by_name = view;
	}

	public boolean hasViewByName() {
		return null != this.getViewByName();
	}

	public ImmutableSortedSet<Integer> getViewByFid() {
		return this.view_by_fid;
	}

	public void setViewByFid (ImmutableSortedSet<Integer> view) {
		this.view_by_fid = view;
	}

	public boolean hasViewByFid() {
		return null != this.getViewByFid();
	}

	public Handle getItemHandle() {
		return this.item_handle;
	}

	public boolean hasItemHandle() {
		return null != this.getItemHandle();
	}

	public void setItemHandle (Handle item_handle) {
		this.item_handle = item_handle;
	}

	public void clearItemHandle() {
		this.setItemHandle (null);
	}

	public int referenceExchangeAdd (int val) {
		final int old = this.reference_count;
		this.reference_count += val;
		return old;
	}

	public int getReferenceCount() {
		return this.reference_count;
	}

	public Handle getTimerHandle() {
		return this.timer_handle;
	}

	public boolean hasTimerHandle() {
		return null != this.getTimerHandle();
	}

	public void setTimerHandle (Handle timer_handle) {
		this.timer_handle = timer_handle;
	}

	public void clearTimerHandle() {
		this.setTimerHandle (null);
	}

}

/* eof */
