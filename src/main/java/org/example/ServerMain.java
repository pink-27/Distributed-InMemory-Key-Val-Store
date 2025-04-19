package org.example;

import org.example.server.proxy.Proxy;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class ServerMain
{
    public static void main( String[] args ) throws IOException {
        Proxy proxy = new Proxy();
        proxy.handleClient();
    }


}
// mvn compile exec:java -Dexec.mainClass=org.example.ServerMain
//