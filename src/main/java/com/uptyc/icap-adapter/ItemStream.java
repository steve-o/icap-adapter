/* Item stream runtime.
 */

package com.uptyc.IcapAdapter;

import com.reuters.rfa.common.Handle;

public class ItemStream {
/* Fixed name for this stream. */
	private String item_name;

/* Service origin, e.g. IDN_RDF */
	private String service_name;

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
