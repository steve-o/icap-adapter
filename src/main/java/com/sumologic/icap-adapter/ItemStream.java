/* Item stream runtime.
 */

package com.sumologic.IcapAdapter;

import com.google.common.collect.ImmutableSortedSet;
import com.reuters.rfa.common.Handle;

public class ItemStream {
/* Fixed name for this stream. */
	private String item_name;

/* Service origin, e.g. IDN_RDF */
	private String service_name;

/* Pseudo-view parameter, an array of field names */
	private ImmutableSortedSet<String> view_by_name;
	private ImmutableSortedSet<Integer> view_by_fid;

/* Subscription handle which is valid from login success to login close. */
	private Handle item_handle;

/* Performance counters */

	public ItemStream() {
		this.setItemHandle (null);
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
}

/* eof */
