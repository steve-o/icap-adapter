package com.sumologic.IcapAdapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.api.VmOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.reuters.tibmsg.Tib;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;
import com.reuters.tibmsg.TibMfeedDict;

@VmOptions("-server")
public class TibMsgBenchmark {

	private TibMsg mfeed, tibmsg;
	private TibField field;
	private ImmutableSortedSet<String> name_set;
	private ImmutableSortedSet<Integer> fid_set;
	private Map<String, String> name_hashmap, name_treemap;
	private Map<Integer, String> fid_hashmap, fid_treemap;
	private Map<String, TibField> value_hashmap, value_treemap;
	private int bid_fid, ask_fid;

	private static final char FS = 0x1c;	// file separator
	private static final char GS = 0x1d;	// group separator
	private static final char RS = 0x1e;	// record separator
	private static final char US = 0x1f;	// unit separator

/* Global dictionary, read only once:
 * com.reuters.tibmsg.TibException: Specified data dictionary has already been loaded/unpacked
 */
	static {
		try {
			TibMsg.ReadMfeedDictionary ("appendix_a", "enumtype.def");
		} catch (com.reuters.tibmsg.TibException e) {
			throw new AssertionError (e);
		}
	}

/* BID            : REAL      8 : 21.42 <18>
 * ASK            : REAL      8 : 21.43 <18>
 * BIDSIZE        : REAL      8 : 7.0 <0>
 * ASKSIZE        : REAL      8 : 7.0 <0>
 * PRC_QL_CD      : STRING    1 : "0" <261>
 * BID_MMID1      : STRING    4 : "NAS "
 * ASK_MMID1      : STRING    4 : "NAS "
 * GV1_TEXT       : STRING    6 : "A     "
 * QUOTIM         : STRING    8 : "14:33:44" <260>
 * PRC_QL3        : STRING    1 : "0" <261>
 * QUOTIM_MS      : REAL      8 : 5.2424789E7 <0>
 */
	@BeforeExperiment void setUp() {
		try {
/* build dictionary map */
			final Map<String, String> map = Maps.newLinkedHashMap();
			if (TibMsg.GetMfeedDictNumFids() > 0) {
				final TibMfeedDict mfeed_dictionary[] = TibMsg.GetMfeedDictionary();
				for (int i = 0; i < mfeed_dictionary.length; i++) {
					if (null == mfeed_dictionary[i]) continue;
					final int fid = (i > TibMsg.GetMfeedDictPosFids()) ? (TibMsg.GetMfeedDictPosFids() - i) : i;
					map.put (mfeed_dictionary[i].fname, Integer.toString (fid));
				}
			}
			final ImmutableMap<String, String> appendix_a = ImmutableMap.copyOf (map);
/* copy out BID and ASK fids */
			bid_fid = Integer.parseInt (appendix_a.get ("BID"));
			ask_fid = Integer.parseInt (appendix_a.get ("ASK"));
			StringBuilder sb = new StringBuilder();
/* <FS>316<US>TAG<GS>RIC[<US>RTL]{<RS>FID<US>VALUE}<FS> */
			sb	.append (FS)
				.append ("316")		// Update Record (316)
				.append (US)
				.append ("XX")		// TAG is a two-character code used to track response.
				.append (GS)
				.append ("TIBX.O")	// RIC
				.append (US)
				.append ("31424")	// RTL: Record transaction level.
/* <RS>FID<US>VALUE */
				.append (RS).append (appendix_a.get ("BID")).append (US).append ("+21.42")
				.append (RS).append (appendix_a.get ("ASK")).append (US).append ("+21.43")
				.append (RS).append (appendix_a.get ("BIDSIZE")).append (US).append ("+7")
				.append (RS).append (appendix_a.get ("ASKSIZE")).append (US).append ("+7")
				.append (RS).append (appendix_a.get ("PRC_QL_CD")).append (US).append ("0")
				.append (RS).append (appendix_a.get ("BID_MMID1")).append (US).append ("NAS ")
				.append (RS).append (appendix_a.get ("ASK_MMID1")).append (US).append ("NAS ")
				.append (RS).append (appendix_a.get ("GV1_TEXT")).append (US).append ("A     ")
				.append (RS).append (appendix_a.get ("QUOTIM")).append (US).append ("14:33:44")
				.append (RS).append (appendix_a.get ("PRC_QL3")).append (US).append ("0")
				.append (RS).append (appendix_a.get ("QUOTIM_MS")).append (US).append ("+52424789")
				.append (FS);
			byte[] raw = sb.toString().getBytes();
			mfeed = new TibMsg();
			mfeed.UnPack (raw);
		} catch (com.reuters.tibmsg.TibException e) {
			throw new AssertionError (e);
		}
		try {
			tibmsg = new TibMsg();
			tibmsg.Append ("BID", 21.42, Tib.HINT_DECIMAL_2);
			tibmsg.Append ("ASK", 21.43, Tib.HINT_DECIMAL_2);
			tibmsg.Append ("BIDSIZE", 7.0, Tib.HINT_DENOM_NONE);
			tibmsg.Append ("ASKSIZE", 7.0, Tib.HINT_DENOM_NONE);
// VALUE:0 DISPLAY:"   " MEANING:normal market or not allocated
			tibmsg.Append ("PRC_QL_CD", "0", Tib.HINT_MFEED_ENUMERATED);
			tibmsg.Append ("BID_MMID1", "NAS ");
			tibmsg.Append ("ASK_MMID1", "NAS ");
			tibmsg.Append ("GV1_TEXT", "A     ");
			tibmsg.Append ("QUOTIM", "14:33:44", Tib.HINT_MFEED_TIME_SECONDS);
// VALUE:0 DISPLAY:"   " MEANING:normal market or not allocated
			tibmsg.Append ("PRC_QL3", "0", Tib.HINT_MFEED_ENUMERATED);
			tibmsg.Append ("QUOTIM_MS", 52424789.0, Tib.HINT_DENOM_NONE);
		} catch (com.reuters.tibmsg.TibException e) {
			throw new AssertionError (e);
		}

		field = new TibField();
		name_set = ImmutableSortedSet.of ("BID", "ASK");
		fid_set = ImmutableSortedSet.copyOf (new Integer[] { bid_fid, ask_fid });
		name_hashmap = Maps.newHashMapWithExpectedSize (2);
		name_treemap = Maps.newTreeMap();
		fid_hashmap = Maps.newHashMapWithExpectedSize (2);
		fid_treemap = Maps.newTreeMap();
		value_hashmap = Maps.newHashMap();
		value_treemap = Maps.newTreeMap();
	}

	@Benchmark long MarketFeedGet (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			try {
				dummy |= mfeed.Get ("BID").StringData().hashCode();
				dummy |= mfeed.Get ("ASK").StringData().hashCode();
			} catch (com.reuters.tibmsg.TibException e) {
				throw new AssertionError (e);
			}
		}
		return dummy;
	}

	@Benchmark long TibMsgGet (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			try {
				dummy |= tibmsg.Get ("BID").StringData().hashCode();
				dummy |= tibmsg.Get ("ASK").StringData().hashCode();
			} catch (com.reuters.tibmsg.TibException e) {
				throw new AssertionError (e);
			}
		}
		return dummy;
	}

	@Benchmark long MarketFeedIterateByName (int reps) {
		long dummy = 0;
		String bid, ask;
		for (int i = 0; i < reps; ++i) {
			bid = ask = null;
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (field.Name().equals ("BID")) 
					bid = field.StringData();
				else if (field.Name().equals ("ASK"))
					ask = field.StringData();
				else continue;
				if (null != bid && null != ask) break;
			}
			dummy |= bid.hashCode();
			dummy |= ask.hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedIterateByFid (int reps) {
		long dummy = 0;
		String bid, ask;
		for (int i = 0; i < reps; ++i) {
			bid = ask = null;
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (field.MfeedFid() == bid_fid)
					bid = field.StringData();
				else if (field.MfeedFid() == ask_fid)
					ask = field.StringData();
				else continue;
				if (null != bid && null != ask) break;
			}
			dummy |= bid.hashCode();
			dummy |= ask.hashCode();
		}
		return dummy;
	}

	@Benchmark long TibMsgIterate (int reps) {
		long dummy = 0;
		String bid, ask;
		for (int i = 0; i < reps; ++i) {
			bid = ask = null;
			for (int status = field.First (tibmsg);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (field.Name().equals ("BID"))
					bid = field.StringData();
				else if (field.Name().equals ("ASK"))
					ask = field.StringData();
				else continue;
				if (null != bid && null != ask) break;
			}
			dummy |= bid.hashCode();
			dummy |= ask.hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedNameHashMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			name_hashmap.clear();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (name_set.contains (field.Name())) {
					name_hashmap.put (field.Name(), field.StringData());
					if (name_hashmap.size() == name_set.size()) break;
				}
			}
			dummy |= name_hashmap.get ("BID").hashCode();
			dummy |= name_hashmap.get ("ASK").hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedFidHashMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			fid_hashmap.clear();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (fid_set.contains (field.MfeedFid())) {
					fid_hashmap.put (field.MfeedFid(), field.StringData());
					if (fid_hashmap.size() == fid_set.size()) break;
				}
			}
			dummy |= fid_hashmap.get (bid_fid).hashCode();
			dummy |= fid_hashmap.get (ask_fid).hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedValueHashMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			value_hashmap.clear();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				value_hashmap.put (field.Name(), new TibField (field.Name(), field.Data(), field.HintData()));
			}

			dummy |= value_hashmap.get ("BID").StringData().hashCode();
			dummy |= value_hashmap.get ("ASK").StringData().hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedNameTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			name_treemap.clear();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (name_set.contains (field.Name())) {
					name_treemap.put (field.Name(), field.StringData());
					if (name_treemap.size() == name_set.size()) break;
				}
			}
			dummy |= name_treemap.get ("BID").hashCode();
			dummy |= name_treemap.get ("ASK").hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedFidTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			fid_treemap.clear();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (fid_set.contains (field.MfeedFid())) {
					fid_treemap.put (field.MfeedFid(), field.StringData());
					if (fid_treemap.size() == fid_set.size()) break;
				}
			}
			dummy |= fid_treemap.get (bid_fid).hashCode();
			dummy |= fid_treemap.get (ask_fid).hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedValueTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			value_treemap.clear();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				value_treemap.put (field.Name(), new TibField (field.Name(), field.Data(), field.HintData()));
			}

			dummy |= value_treemap.get ("BID").StringData().hashCode();
			dummy |= value_treemap.get ("ASK").StringData().hashCode();
		}
		return dummy;
	}
}

/* eof */
