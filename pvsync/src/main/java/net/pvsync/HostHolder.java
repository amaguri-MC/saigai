package net.pvsync;

/** Host name the client typed (handshake), kept on the connection. */
public interface HostHolder {
    String pv$host();

    void pv$setHost(String host);
}
