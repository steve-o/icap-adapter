/* Rfa context.
 */

package com.uptyc.IcapAdapter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Joiner;
import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.common.Context;

public class Rfa {

	private static Logger LOG = LogManager.getLogger (Rfa.class.getName());
	private static final String LINE_SEPARATOR = System.getProperty ("line.separator");

	private Config config;
	private ConfigDb rfa_config;

	private static final String RSSL_PROTOCOL       = "rssl";
	private static final String SSLED_PROTOCOL      = "ssled";

	public Rfa (Config config) {
		this.config = config;
	}

	private static String fixRfaStringPath (String rfa_string) {
/* Convert path format from Java ConfigProvider to X11 */
		String with_dots = rfa_string.replace ('/', '.');
/* Strip leading path delimiter */
		if (with_dots.startsWith (".")) {
			with_dots = with_dots.substring (1);
		}
/* Prefix default namespace */
		if (with_dots.startsWith ("Connections.") || with_dots.startsWith ("Sessions.")) {
//			with_dots = "_Default." + with_dots;
		}
		return with_dots;
	}

	public void init() throws Exception {
		LOG.info ("Initializing RFA.");
		Context.initialize (null);

/* Populate Config Database.
 */
		LOG.info ("Populating RFA config database.");
		ConfigDb staging = new ConfigDb();

		for (SessionConfig session_config : this.config.getSessions())
		{
			final String session_name = session_config.getSessionName(),
				connection_name = session_config.getConnectionName();
			String name, value;

/* Session list */
			name = "/Sessions/" + session_name + "/connectionList";
			value = connection_name;
			staging.addVariable (this.fixRfaStringPath (name), value);
/* Logging per connection */
			name = "/Connections/" + connection_name + "/logFileName";
			value = "none";
			staging.addVariable (this.fixRfaStringPath (name), value);
/* List of servers */
			name = "/Connections/" + connection_name + "/serverList";
			value = Joiner.on (",").join (session_config.getServers());
			staging.addVariable (this.fixRfaStringPath (name), value);
/* Default port */
			if (!session_config.getDefaultPort().isEmpty()) {
				name = "/Connections/" + connection_name + "/portNumber";
				value = session_config.getDefaultPort();
				staging.addVariable (this.fixRfaStringPath (name), value);
			}

/* Communications protocol */
			name = "/Connections/" + connection_name + "/connectionType";
			if (session_config.getProtocol().equalsIgnoreCase (RSSL_PROTOCOL))
			{
				value = "RSSL";
				staging.addVariable (this.fixRfaStringPath (name), value);
			}
			else if (session_config.getProtocol().equalsIgnoreCase (SSLED_PROTOCOL))
			{
				value = "SSLED";
				staging.addVariable (this.fixRfaStringPath (name), value);

				name = "/Connections/" + connection_name + "/userName";
				StringBuilder username = new StringBuilder (session_config.getUserName());
				if (!session_config.getApplicationId().isEmpty()) {
					username.append ('+');
					username.append (session_config.getApplicationId());
					if (!session_config.getPosition().isEmpty()) {
						username.append ('+');
						username.append (session_config.getPosition());
					}
				}
				value = username.toString();
				staging.addVariable (this.fixRfaStringPath (name), value);
			}
			else
			{
				throw new Exception ("Unsupported transport protocol \"" + session_config.getProtocol() + "\".");
			}
		}

		LOG.info ("Merging RFA config database with staging database.");
		this.rfa_config = staging;
		Context.initialize (this.rfa_config);

/* TODO: Java properties override */

/* Dump effective Java properties configuration */
		LOG.info ("Dumping configuration database:{}{}", LINE_SEPARATOR, this.rfa_config);

		LOG.info ("RFA initialization complete.");
	}

	public void clear() {
		LOG.info ("Closing RFA.");
		Context.uninitialize();
	}
}

/* eof */
