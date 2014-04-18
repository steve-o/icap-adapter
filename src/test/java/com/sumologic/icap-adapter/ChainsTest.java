/*
 */

package com.sumologic.IcapAdapter;

import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

public class ChainsTest extends TestCase {

	public void testIsChain() {
		assertTrue ("0#.FTSE", Chains.isChain ("0#.FTSE"));
		assertFalse ("1#.FTSE", Chains.isChain ("1#.FTSE"));
		assertFalse (".FTSE", Chains.isChain (".FTSE"));
		assertTrue (".AV.O", Chains.isChain (".AV.O"));
		assertFalse ("MSFT.O", Chains.isChain ("MSFT.O"));
	}

	public void testIsChainLink() {
		assertTrue ("0#.FTSE", Chains.isChainLink ("0#.FTSE"));
		assertTrue ("1#.FTSE", Chains.isChainLink ("1#.FTSE"));
		assertFalse (".FTSE", Chains.isChainLink (".FTSE"));
		assertFalse (".AV.O", Chains.isChainLink (".AV.O"));
		assertFalse ("MSFT.O", Chains.isChainLink ("MSFT.O"));
	}

	public void testApplyFieldDictionaryNullPointer() {
		try {
			Chains.ApplyFieldDictionary (null);
			fail();
		} catch (NullPointerException expected) {}
	}

	public void testApplyFieldDictionary() {
		ImmutableMap<String, Integer> dict = new ImmutableMap.Builder<String, Integer>()
			.put ("PREV_LR", 237)
			.put ("NEXT_LR", 238)
			.put ("REF_COUNT", 239)
			.put ("LINK_1", 240)
			.put ("LINK_2", 241)
			.put ("LINK_3", 242)
			.put ("LINK_4", 243)
			.put ("LINK_5", 244)
			.put ("LINK_6", 245)
			.put ("LINK_7", 246)
			.put ("LINK_8", 247)
			.put ("LINK_9", 248)
			.put ("LINK_10", 249)
			.put ("LINK_11", 250)
			.put ("LINK_12", 251)
			.put ("LINK_13", 252)
			.put ("LINK_14", 253)
			.put ("LONGLINK1", 800)
			.put ("LONGLINK2", 801)
			.put ("LONGLINK3", 802)
			.put ("LONGLINK4", 803)
			.put ("LONGLINK5", 804)
			.put ("LONGLINK6", 805)
			.put ("LONGLINK7", 806)
			.put ("LONGLINK8", 807)
			.put ("LONGLINK9", 808)
			.put ("LONGLINK10", 809)
			.put ("LONGLINK11", 810)
			.put ("LONGLINK12", 811)
			.put ("LONGLINK13", 812)
			.put ("LONGLINK14", 813)
			.put ("LONGPREVLR", 814)
			.put ("LONGNEXTLR", 815)
			.build();
		Chains.ApplyFieldDictionary (dict);
		assertEquals (Chains.NEXT_LR_FID, 238);
		assertEquals (Chains.LINK_1_FID, 240);
		assertEquals (Chains.LINK_14_FID, 253);
		assertEquals (Chains.LONGLINK1_FID, 800);
		assertEquals (Chains.LONGLINK14_FID, 813);
		assertEquals (Chains.LONGNEXTLR_FID, 815);
	}

}

/* eof */
