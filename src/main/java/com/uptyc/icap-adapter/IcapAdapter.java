/* ICAP Streaming Adapter.
 */

package com.uptyc.IcapAdapter;

import java.io.*;
import java.util.*;
import java.net.*;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Joiner;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.session.Session;

public class IcapAdapter {

/* Application configuration. */
	private Config config;

/* RFA context. */
	private Rfa rfa;

/* RFA asynchronous event queue. */
	private EventQueue event_queue;

/* RFA consumer */
	private Consumer consumer;

/* Instrument list. */
	private Instrument[] instruments;
	private List<ItemStream> streams;

	private static Logger LOG = LogManager.getLogger (IcapAdapter.class.getName());

	private static final String RSSL_PROTOCOL	= "rssl";
	private static final String SSLED_PROTOCOL	= "ssled";

	private static final String SESSION_OPTION	= "session";
	private static final String SYMBOL_PATH_OPTION	= "symbol-path";
	private static final String HELP_OPTION		= "help";
	private static final String VERSION_OPTION	= "version";

	private static Options buildOptions() {
		Options opts = new Options();

		Option help = OptionBuilder.withLongOpt (HELP_OPTION)
					.withDescription ("print this message")
					.create ("h");
		opts.addOption (help);

		Option version = OptionBuilder.withLongOpt (VERSION_OPTION)
					.withDescription ("print version information and exit")
					.create();
		opts.addOption (version);

		Option session = OptionBuilder.hasArg()
					.isRequired()
					.withArgName ("uri")
					.withDescription ("TREP-RT session declaration")
					.withLongOpt (SESSION_OPTION)
					.create();
		opts.addOption (session);

		Option symbol_path = OptionBuilder.hasArg()
					.isRequired()
					.withArgName ("file")
					.withDescription ("read from symbol path")
					.withLongOpt (SYMBOL_PATH_OPTION)
					.create();
		opts.addOption (symbol_path);

		return opts;
	}

	private static void printHelp (Options options) {
		new HelpFormatter().printHelp ("IcapAdapter", options);
	}

	private static Map<String, String> parseQuery (String query) throws UnsupportedEncodingException {
		final Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		final String[] pairs = query.split ("&");
		for (String pair : pairs) {
			int idx = pair.indexOf ("=");
			query_pairs.put (URLDecoder.decode (pair.substring (0, idx), "UTF-8"),
				URLDecoder.decode (pair.substring (idx + 1), "UTF-8"));
		}
		return query_pairs;
	}

	private void init (CommandLine line, Options options) throws Exception {
		if (line.hasOption (HELP_OPTION)) {
			this.printHelp (options);
			return;
		}

/* Configuration. */
		this.config = new Config();

		if (line.hasOption (SESSION_OPTION)) {
			final String session = line.getOptionValue (SESSION_OPTION);
			List<SessionConfig> session_configs = new ArrayList<SessionConfig>();
			if (!session.isEmpty()) {
				SessionConfig session_config = new SessionConfig();
				LOG.info ("session: {}", session);
				final URI parsed = new URI (session);
				if (!parsed.getScheme().isEmpty()) {
					session_config.setProtocol (parsed.getScheme());
					LOG.info ("protocol: {}", session_config.getProtocol());
				}
				if (!parsed.getUserInfo().isEmpty()) {
					session_config.setUserName (parsed.getUserInfo());
					LOG.info ("username: {}", session_config.getUserName());
				} else {
					session_config.setUserName (System.getProperty ("user.name"));
					LOG.info ("username: {} (default)", session_config.getUserName());
				}
				if (!parsed.getHost().isEmpty()) {
					session_config.setServer (parsed.getHost());
					LOG.info ("host: {}", session_config.getServer());
				} else {
					session_config.setServer ("localhost");
					LOG.info ("host: localhost (default)");
				}
				if (0 != parsed.getPort()) {
					session_config.setDefaultPort (Integer.toString (parsed.getPort()));
					LOG.info ("port: {}", session_config.getDefaultPort());
				} else {
					LOG.info ("port: (default)");
				}
				if (!parsed.getPath().isEmpty()) {
					final File path = new File (parsed.getPath());
					session_config.setServiceName (path.getName());
					LOG.info ("service: {}", session_config.getServiceName());
				}
				if (!parsed.getQuery().isEmpty()) {
/* For each key-value pair, i.e. ?a=x&b=y&c=z -> (a,x) (b,y) (c,z)
 */
					Map<String, String> query = this.parseQuery (parsed.getQuery());
					final String application_id = query.get ("application-id"),
						instance_id = query.get ("instance-id"),
						position = query.get ("position"),
						server_list = query.get ("server-list");
					if (null != application_id) {
						session_config.setApplicationId (application_id);
						LOG.info ("application-id: {}", session_config.getApplicationId());
					}
					if (null != instance_id) {
						session_config.setInstanceId (instance_id);
						LOG.info ("instance-id: {}", session_config.getInstanceId());
					}
					if (null != position) {
						session_config.setPosition (position);
						LOG.info ("position: {}", session_config.getPosition());
					} else {
						session_config.setPosition (InetAddress.getLocalHost().getHostAddress() + "/"
							+ InetAddress.getLocalHost().getHostName());
						LOG.info ("position: {} (default)", session_config.getPosition());
					}
					if (null != server_list) {
						session_config.setServers (server_list.split (","));
/* String.join() */
						LOG.info ("server-list: {}", 
							Joiner.on (", ").join (session_config.getServers()));
					}
				}
/* Boiler plate naming. */
				session_config.setSessionName ("MySession");
				session_config.setConnectionName ("MyConnection");
				session_config.setConsumerName ("MyConsumer");
				session_configs.add (session_config);
			}
			if (!session_configs.isEmpty()) {
				SessionConfig[] array = session_configs.toArray (new SessionConfig[session_configs.size()]);
				this.config.setSessions (array);
			}
		}

/* Symbol list. */
		if (line.hasOption (SYMBOL_PATH_OPTION)) {
			this.config.setSymbolPath (line.getOptionValue (SYMBOL_PATH_OPTION));
			File symbol_path = new File (this.config.getSymbolPath());
			if (symbol_path.canRead()) {
				List<Instrument> instruments = new ArrayList<Instrument> ();
				Scanner line_scanner = new Scanner (symbol_path);
				try {
					while (line_scanner.hasNextLine()) {
						Scanner field_scanner = new Scanner (line_scanner.nextLine());
						field_scanner.useDelimiter (",");
						String service, symbol_name;
						List<String> fields = new LinkedList<String>();
						if (!field_scanner.hasNext())
							throw new IOException ("Missing service field.");
						service = field_scanner.next();
						if (!field_scanner.hasNext())
							throw new IOException ("Missing symbol name field.");
						symbol_name = field_scanner.next();
						while (field_scanner.hasNext())
							fields.add (field_scanner.next());
						if (!fields.isEmpty()) {
							Instrument new_instrument = new Instrument (service, symbol_name, fields.toArray (new String[fields.size()]));
							instruments.add (new_instrument);
							LOG.info ("symbol: {}", new_instrument);
						}
					}
				} finally {
					line_scanner.close();
				}
				this.instruments = instruments.toArray (new Instrument[instruments.size()]);
				LOG.info ("Read {} symbols from {}.", this.instruments.length, symbol_path);
			}
		}

		LOG.info ("{}", this.config);

/* RFA Context. */
		this.rfa = new Rfa (this.config);
		this.rfa.init();

/* RFA asynchronous event queue. */
		this.event_queue = EventQueue.create (this.config.getEventQueueName());

/* RFA consumer */
		this.consumer = new Consumer (this.config.getSession(),
					this.rfa,
					this.event_queue);
		this.consumer.init();

/* Create state for subscribed RIC. */
		this.streams = new ArrayList<ItemStream> (this.instruments.length);
		for (Instrument instrument : this.instruments) {
			ItemStream stream = new ItemStream();
			this.consumer.createItemStream (instrument, stream);
			this.streams.add (stream);
			LOG.info ("{}", instrument);
		}

	}

	private void run (CommandLine line, Options options) throws Exception {
		this.init (line, options);
		this.mainloop();
		this.clear();
	}

	private void mainloop() {
		try {
			while (true) {
				this.event_queue.dispatch (Dispatchable.INFINITE_WAIT);
			}
		} catch (DispatchException e) {
			e.printStackTrace();
		}
	}

	private void clear() {
		if (null != this.event_queue && this.event_queue.isActive())
			this.event_queue.deactivate();

		if (null != this.consumer)
			this.consumer.clear();

		if (null != this.event_queue)
			this.event_queue.destroy();

		if (null != this.rfa)
			this.rfa.clear();
	}

	public static void main (String[] args) throws Exception {
		final Options options = IcapAdapter.buildOptions();
		final CommandLine line = new PosixParser().parse (options, args);
		IcapAdapter adapter = new IcapAdapter();
		adapter.run (line, options);
	}
}

/* eof */
