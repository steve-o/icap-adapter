package com.uptyc.IcapAdapter;

import java.util.Iterator;
import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.api.VmOptions;
import com.google.common.base.Optional;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMIterable;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;

@VmOptions("-server")
public class OmmBenchmark {

	private static FieldDictionary dictionary;
	private static FidDef biddef, askdef;
	private OMMPool pool;
	private OMMMsg msg;

/* Global dictionary, read only once:
 * com.reuters.tibmsg.TibException: Specified data dictionary has already been loaded/unpacked
 */
	static {
		Context.initialize();
		dictionary = FieldDictionary.create();
		FieldDictionary.readRDMFieldDictionary (dictionary, "RDMFieldDictionary");
		FieldDictionary.readEnumTypeDef (dictionary, "enumtype.def");
		biddef = dictionary.getFidDef ((short)22);
		askdef = dictionary.getFidDef ((short)25);
	}

/* BID            : REAL      8 : 21.42 <18>		// TSS_HINT_DECIMAL_2
 * ASK            : REAL      8 : 21.43 <18>
 * BIDSIZE        : REAL      8 : 7.0 <0>		// TSS_HINT_DENOM_NONE
 * ASKSIZE        : REAL      8 : 7.0 <0>
 * PRC_QL_CD      : STRING    1 : "0" <261>		// TSS_HINT_MFEED_ENUMERATED
 * BID_MMID1      : STRING    4 : "NAS "
 * ASK_MMID1      : STRING    4 : "NAS "
 * GV1_TEXT       : STRING    6 : "A     "
 * QUOTIM         : STRING    8 : "14:33:44" <260>	// TSS_HINT_MFEED_TIME_SECONDS
 * PRC_QL3        : STRING    1 : "0" <261>
 * QUOTIM_MS      : REAL      8 : 5.2424789E7 <0>
 *
 * <!-- rwfMajorVer="14" rwfMinorVer="0" -->
 * <updateMsg domainType="RSSL_DMT_MARKET_PRICE" streamId="5" containerType="RSSL_DT_FIELD_LIST" flags="0x10 (RSSL_UPMF_HAS_SEQ_NUM)" updateType="1" seqNum="3632" dataSize="70">
 *     <dataBody>
 *         <fieldList flags="0x8 (RSSL_FLF_HAS_STANDARD_DATA)">
 *             <fieldEntry fieldId="22" dataType="RSSL_DT_REAL" data="21.42"/>
 *             <fieldEntry fieldId="25" dataType="RSSL_DT_REAL" data="21.43"/>
 *             <fieldEntry fieldId="30" dataType="RSSL_DT_REAL" data="7"/>
 *             <fieldEntry fieldId="31" dataType="RSSL_DT_REAL" data="7"/>
 *             <fieldEntry fieldId="118" dataType="RSSL_DT_ENUM" data="0"/>
 *             <fieldEntry fieldId="293" dataType="RSSL_DT_RMTES_STRING" data="NAS"/>
 *             <fieldEntry fieldId="296" dataType="RSSL_DT_RMTES_STRING" data="NAS"/>
 *             <fieldEntry fieldId="1000" dataType="RSSL_DT_RMTES_STRING" data="A"/>
 *             <fieldEntry fieldId="1025" dataType="RSSL_DT_TIME" data=" 14:33:44:000"/>
 *             <fieldEntry fieldId="3264" dataType="RSSL_DT_ENUM" data="0"/>
 *             <fieldEntry fieldId="3855" dataType="RSSL_DT_UINT" data="52424789"/>
 *         </fieldList>
 *     </dataBody>
 * </updateMsg>
 */
	@BeforeExperiment void setUp() {
		pool = OMMPool.create ("RWF",
				14,	/* RWF major version */
				0,	/* RWF minor version */
				OMMPool.SINGLE_THREADED);
		OMMEncoder encoder = pool.acquireEncoder();
		encoder.initialize (OMMTypes.MSG, 500);
		msg = pool.acquireMsg();
		msg.setMsgType (OMMMsg.MsgType.UPDATE_RESP);
		msg.setMsgModelType (RDMMsgTypes.MARKET_PRICE);
		msg.setSeqNum (3632);
		msg.setRespTypeNum (RDMInstrument.Update.QUOTE);
		encoder.encodeMsgInit (msg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
		encoder.encodeFieldListInit (OMMFieldList.HAS_STANDARD_DATA,
				(short)1,	/* dictionaryId */
				(short)78,	/* fieldListNumber */
				(short)0);	/* dataDefId */
// BID
		encoder.encodeFieldEntryInit ((short)22, OMMTypes.REAL);
		encoder.encodeReal ((long)(21.42 * 100), OMMNumeric.EXPONENT_NEG2);
// ASK
		encoder.encodeFieldEntryInit ((short)25, OMMTypes.REAL);
		encoder.encodeReal ((long)(21.43 * 100), OMMNumeric.EXPONENT_NEG2);
// BIDSIZE
		encoder.encodeFieldEntryInit ((short)30, OMMTypes.REAL);
		encoder.encodeReal (7L, OMMNumeric.EXPONENT_0);
// ASKSIZE
		encoder.encodeFieldEntryInit ((short)31, OMMTypes.REAL);
		encoder.encodeReal (7L, OMMNumeric.EXPONENT_0);
// PRC_QL_CD
 		encoder.encodeFieldEntryInit ((short)118, OMMTypes.ENUM);
		encoder.encodeEnum (0);
// BID_MMID1
 		encoder.encodeFieldEntryInit ((short)293, OMMTypes.RMTES_STRING);
		encoder.encodeString ("NAS", OMMTypes.RMTES_STRING);
// ASK_MMID1
 		encoder.encodeFieldEntryInit ((short)296, OMMTypes.RMTES_STRING);
		encoder.encodeString ("NAS", OMMTypes.RMTES_STRING);
// GV1_TEXT
 		encoder.encodeFieldEntryInit ((short)1000, OMMTypes.RMTES_STRING);
		encoder.encodeString ("A", OMMTypes.RMTES_STRING);
// QUOTIM
 		encoder.encodeFieldEntryInit ((short)1025, OMMTypes.TIME);
		encoder.encodeTime (14, 33, 44, 0);
// PRC_QL3
 		encoder.encodeFieldEntryInit ((short)3264, OMMTypes.ENUM);
		encoder.encodeEnum (0);
// QUOTIM_MS
 		encoder.encodeFieldEntryInit ((short)3855, OMMTypes.UINT);
		encoder.encodeUInt (52424789L);
		encoder.encodeAggregateComplete();
		msg = (OMMMsg)encoder.getEncodedObject();
	}

	@AfterExperiment void cleanUp() {
		pool.releaseMsg (msg);
	}

	@Benchmark long OmmFind (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			final OMMFieldList fieldlist = (OMMFieldList)msg.getPayload();
			dummy |= fieldlist.find (biddef.getFieldId()).getData (biddef.getOMMType()).toString().hashCode();
			dummy |= fieldlist.find (askdef.getFieldId()).getData (askdef.getOMMType()).toString().hashCode();
		}
		return dummy;
	}

	@Benchmark long OmmIterate (int reps) {
		long dummy = 0;
		String bid, ask;
		for (int i = 0; i < reps; ++i) {
			bid = ask = null;
			for (Iterator<?> it = ((OMMIterable)msg.getPayload()).iterator();
				it.hasNext();)
			{
				final OMMFieldEntry field = (OMMFieldEntry)it.next();
				if (biddef.getFieldId() == field.getFieldId())
					bid = field.getData (biddef.getOMMType()).toString();
				else if (askdef.getFieldId() == field.getFieldId())
					ask = field.getData (askdef.getOMMType()).toString();
				if (null != bid && null != ask) break;
			}
			dummy |= bid.hashCode();
			dummy |= ask.hashCode();
		}
		return dummy;
	}
}

/* eof */
