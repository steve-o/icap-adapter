/* Chain subscriber interface.  Ordering or updates are ignored.
 */

package com.sumologic.IcapAdapter;

public interface ChainListener {
	public void OnAddEntry (String item_name, java.lang.Object closure);
	public void OnRemoveEntry (String item_name, java.lang.Object closure);
}

/* eof */
