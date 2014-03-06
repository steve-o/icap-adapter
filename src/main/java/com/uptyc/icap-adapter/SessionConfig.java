/* Adapter configuration.
 */

package com.uptyc.IcapAdapter;

public class SessionConfig {
//  RFA session name, one session contains a horizontal scaling set of connections.
	private String session_name;

//  RFA connection name, used for logging.
	private String connection_name;

//  RFA consumer name.
	private String consumer_name;

//  Protocol name, RSSL or SSL.
	private String protocol;

//  TREP-RT service name, e.g. IDN_RDF.
	private String service_name;

//  TREP-RT ADH hostname or IP address.
	private String[] servers;

//  Default TREP-RT R/SSL port, e.g. 14002, 14003, 8101.
	private String default_port;

/* DACS application Id.  If the server authenticates with DACS, the consumer
 * application may be required to pass in a valid ApplicationId.
 * Range: "" (None) or 1-511 as an Ascii string.
 */
	private String application_id;

/* InstanceId is used to differentiate applications running on the same host.
 * If there is more than one noninteractive provider instance running on the
 * same host, they must be set as a different value by the provider
 * application. Otherwise, the infrastructure component which the providers
 * connect to will reject a login request that has the same InstanceId value
 * and cut the connection.
 * Range: "" (None) or any Ascii string, presumably to maximum RFA_String length.
 */
	private String instance_id;

/* DACS username, frequently non-checked and set to similar: user1.
 */
	private String user_name;

/* DACS position, the station which the user is using.
 * Range: "" (None) or "<IPv4 address>/hostname" or "<IPv4 address>/net"
 */
	private String position;

	public String getSessionName() {
		return this.session_name;
	}

	public void setSessionName (String session_name) {
		this.session_name = session_name;
	}

	public String getConnectionName() {
		return this.connection_name;
	}

	public void setConnectionName (String connection_name) {
		this.connection_name = connection_name;
	}

	public String getConsumerName() {
		return this.consumer_name;
	}

	public void setConsumerName (String consumer_name) {
		this.consumer_name = consumer_name;
	}

	public String getProtocol() {
		return this.protocol;
	}

	public void setProtocol (String protocol) {
		this.protocol = protocol;
	}

	public String getServiceName() {
		return this.service_name;
	}

	public void setServiceName (String service_name) {
		this.service_name = service_name;
	}

	public String[] getServers() {
		return this.servers;
	}

	public void setServers (String[] servers) {
		this.servers = servers;
	}

/* support for singular server */
	public String getServer() {
		return this.getServers()[0];
	}

	public void setServer (String server) {
		String[] array = { server };
		this.setServers (array);
	}

	public String getDefaultPort() {
		return this.default_port;
	}

	public void setDefaultPort (String default_port) {
		this.default_port = default_port;
	}

	public String getApplicationId() {
		return this.application_id;
	}

	public void setApplicationId (String application_id) {
		this.application_id = application_id;
	}

	public String getInstanceId() {
		return this.instance_id;
	}

	public void setInstanceId (String instance_id) {
		this.instance_id = instance_id;
	}

	public boolean hasInstanceId() {
		return null != this.instance_id && !this.instance_id.isEmpty();
	}

	public String getUserName() {
		return this.user_name;
	}

	public void setUserName (String user_name) {
		this.user_name = user_name;
	}

	public String getPosition() {
		return this.position;
	}

	public void setPosition (String position) {
		this.position = position;
	}

	@Override
	public String toString() {
		String servers = "";
		for (int i = 0; i < this.servers.length; ++i) {
			if (i > 0) servers += ", ";
			servers += "\"" + this.servers[i] + "\"";
		}
		return "{ " +
			  "\"session_name\": \"" + this.session_name + "\"" +
			", \"connection_name\": \"" + this.connection_name + "\"" +
			", \"consumer_name\": \"" + this.consumer_name + "\"" +
			", \"protocol\": \"" + this.protocol + "\"" +
			", \"service_name\": \"" + this.service_name + "\"" +
			", \"servers\": [" + servers + "]" +
			", \"default_port\": \"" + this.default_port + "\"" +
			", \"application_id\": \"" + this.application_id + "\"" +
			", \"instance_id\": \"" + this.instance_id + "\"" +
			", \"user_name\": \"" + this.user_name + "\"" +
			", \"position\": \"" + this.position + "\"" +
			" }";
	}
}

/* eof */
