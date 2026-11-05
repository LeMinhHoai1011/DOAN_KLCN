package com.example.invoice.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import okhttp3.Dns;

/**
 * "localhost" resolves to both ::1 and 127.0.0.1, but Ollama listens on 127.0.0.1 only by default.
 * After one timed-out call OkHttp postpones the IPv4 route and tries ::1 first; with connection
 * retries disabled every later call then fails instantly ("Failed to connect to localhost/[::1]").
 * For loopback names we therefore offer only the IPv4 loopback; other hosts resolve unchanged.
 */
public final class LoopbackIpv4Dns implements Dns {
	private final Dns delegate;

	public LoopbackIpv4Dns() { this(Dns.SYSTEM); }
	LoopbackIpv4Dns(Dns delegate) { this.delegate = delegate; }

	@Override
	public List<InetAddress> lookup(String hostname) throws UnknownHostException {
		List<InetAddress> addresses = delegate.lookup(hostname);
		boolean loopback = !addresses.isEmpty() && addresses.stream().allMatch(InetAddress::isLoopbackAddress);
		if (!loopback) return addresses;
		List<InetAddress> ipv4 = addresses.stream().filter(Inet4Address.class::isInstance).toList();
		return ipv4.isEmpty() ? addresses : ipv4;
	}
}
