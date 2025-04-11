package org.example;

import org.example.client.Client;
import org.example.server.Server;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class ServerMain
{
    public static void main( String[] args ) throws IOException {
        Server server = new Server();
        server.handleClient();
    }


}
