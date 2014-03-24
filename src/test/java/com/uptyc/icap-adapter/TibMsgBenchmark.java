package com.uptyc.IcapAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.api.VmOptions;
import com.google.common.base.Optional;
import com.reuters.tibmsg.Tib;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

@VmOptions("-server")
public class TibMsgBenchmark {

	private TibMsg mfeed, tibmsg;
	private TibField field;
	private Map map;

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
// BID
				.append (RS).append ("22").append (US).append ("+21.42")
// ASK
				.append (RS).append ("25").append (US).append ("+21.43")
// BIDSIZE
				.append (RS).append ("30").append (US).append ("+7")
// ASKSIZE
				.append (RS).append ("31").append (US).append ("+7")
// PRC_QL_CD
				.append (RS).append ("118").append (US).append ("0")
// BID_MMID1
				.append (RS).append ("293").append (US).append ("NAS ")
// ASK_MMID1
				.append (RS).append ("296").append (US).append ("NAS ")
// GV1_TEXT
				.append (RS).append ("1000").append (US).append ("A     ")
// QUOTIM
				.append (RS).append ("1025").append (US).append ("14:33:44")
// PRC_QL3
				.append (RS).append ("3264").append (US).append ("0")
// QUOTIM_MS
				.append (RS).append ("3855").append (US).append ("+52424789")
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
			tibmsg.Append ("BIDSIZE", 7.0, 0);
			tibmsg.Append ("ASKSIZE", 7.0, 0);
			tibmsg.Append ("PRC_QL_CD", "0", Tib.HINT_MFEED_ENUMERATED);
			tibmsg.Append ("BID_MMID1", "NAS ");
			tibmsg.Append ("ASK_MMID1", "NAS ");
			tibmsg.Append ("GV1_TEXT", "A     ");
			tibmsg.Append ("QUOTIM", "14:33:44", Tib.HINT_MFEED_TIME_SECONDS);
			tibmsg.Append ("PRC_QL3", "0", Tib.HINT_MFEED_ENUMERATED);
			tibmsg.Append ("QUOTIM_MS", 52424789.0, 0);
		} catch (com.reuters.tibmsg.TibException e) {
			throw new AssertionError (e);
		}

		field = new TibField();
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

	@Benchmark long MarketFeedIterate (int reps) {
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
			map = new HashMap<String, Optional<String>> (2);
			map.put ("BID", Optional.absent());
			map.put ("ASK", Optional.absent());
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (map.containsKey (field.Name()))
					map.put (field.Name(), Optional.of (field.StringData()));
			}
			dummy |= ((Optional<String>)map.get ("BID")).get().hashCode();
			dummy |= ((Optional<String>)map.get ("ASK")).get().hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedValueHashMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new HashMap<String, TibField>();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				map.put (field.Name(), new TibField (field.Name(), field.Data(), field.HintData()));
			}

			dummy |= ((TibField)map.get ("BID")).StringData().hashCode();
			dummy |= ((TibField)map.get ("ASK")).StringData().hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedNameTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new HashMap<String, Optional<String>> (2);
			map.put ("BID", Optional.absent());
			map.put ("ASK", Optional.absent());
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (map.containsKey (field.Name()))
					map.put (field.Name(), Optional.of (field.StringData()));
			}
			dummy |= ((Optional<String>)map.get ("BID")).get().hashCode();
			dummy |= ((Optional<String>)map.get ("ASK")).get().hashCode();
		}
		return dummy;
	}

	@Benchmark long MarketFeedValueTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new TreeMap<String, TibField>();
			for (int status = field.First (mfeed);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				map.put (field.Name(), new TibField (field.Name(), field.Data(), field.HintData()));
			}

			dummy |= ((TibField)map.get ("BID")).StringData().hashCode();
			dummy |= ((TibField)map.get ("ASK")).StringData().hashCode();
		}
		return dummy;
	}

	@Benchmark long TibMsgNameHashMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new HashMap<String, Optional<String>> (2);
			map.put ("BID", Optional.absent());
			map.put ("ASK", Optional.absent());
			for (int status = field.First (tibmsg);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (map.containsKey (field.Name()))
					map.put (field.Name(), Optional.of (field.StringData()));
			}
			dummy |= ((Optional<String>)map.get ("BID")).get().hashCode();
			dummy |= ((Optional<String>)map.get ("ASK")).get().hashCode();
		}
		return dummy;
	}

	@Benchmark long TibMsgValueHashMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new HashMap<String, TibField>();
			for (int status = field.First (tibmsg);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				map.put (field.Name(), new TibField (field.Name(), field.Data(), field.HintData()));
			}

			dummy |= ((TibField)map.get ("BID")).StringData().hashCode();
			dummy |= ((TibField)map.get ("ASK")).StringData().hashCode();
		}
		return dummy;
	}

	@Benchmark long TibMsgNameTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new HashMap<String, Optional<String>> (2);
			map.put ("BID", Optional.absent());
			map.put ("ASK", Optional.absent());
			for (int status = field.First (tibmsg);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				if (map.containsKey (field.Name()))
					map.put (field.Name(), Optional.of (field.StringData()));
			}
			dummy |= ((Optional<String>)map.get ("BID")).get().hashCode();
			dummy |= ((Optional<String>)map.get ("ASK")).get().hashCode();
		}
		return dummy;
	}

	@Benchmark long TibMsgValueTreeMap (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			map = new TreeMap<String, TibField>();
			for (int status = field.First (tibmsg);
				TibMsg.TIBMSG_OK == status;
				status = field.Next())
			{
				map.put (field.Name(), new TibField (field.Name(), field.Data(), field.HintData()));
			}

			dummy |= ((TibField)map.get ("BID")).StringData().hashCode();
			dummy |= ((TibField)map.get ("ASK")).StringData().hashCode();
		}
		return dummy;
	}
}

/* eof */
