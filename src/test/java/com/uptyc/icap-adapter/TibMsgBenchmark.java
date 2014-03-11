package com.uptyc.IcapAdapter;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.api.VmOptions;
import com.reuters.tibmsg.TibMsg;

@VmOptions("-server")
public class TibMsgBenchmark {

	private static final TibMsg msg = new TibMsg();

	@BeforeExperiment void setUp() {
		try {
			msg.Append ("BID", 1.12);
		} catch (com.reuters.tibmsg.TibException e) {
			throw new AssertionError (e);
		}
	}

	@Benchmark long timeRandomAccess (int reps) {
		long dummy = 0;
		for (int i = 0; i < reps; ++i) {
			try {
				String data = msg.Get ("BID").StringData();
				dummy |= data.hashCode();
			} catch (com.reuters.tibmsg.TibException e) {
				throw new AssertionError (e);
			}
		}
		return dummy;
	}
}

/* eof */
