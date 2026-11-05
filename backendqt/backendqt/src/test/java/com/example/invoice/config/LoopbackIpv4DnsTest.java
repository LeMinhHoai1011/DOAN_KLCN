package com.example.invoice.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoopbackIpv4DnsTest {
    static InetAddress ip(String literal) throws java.net.UnknownHostException { return InetAddress.getByName(literal); }

    @Test void localhostResolvesToIpv4LoopbackOnly() throws Exception {
        var dns = new LoopbackIpv4Dns(host -> List.of(ip("::1"), ip("127.0.0.1")));
        assertEquals(List.of(ip("127.0.0.1")), dns.lookup("localhost"));
    }

    @Test void ipv6OnlyLoopbackAndRemoteHostsAreUnchanged() throws Exception {
        assertEquals(List.of(ip("::1")), new LoopbackIpv4Dns(host -> List.of(ip("::1"))).lookup("localhost"));
        var remote = List.of(ip("2001:db8::5"), ip("192.168.1.20"));
        assertEquals(remote, new LoopbackIpv4Dns(host -> remote).lookup("ollama-box"));
    }
}
