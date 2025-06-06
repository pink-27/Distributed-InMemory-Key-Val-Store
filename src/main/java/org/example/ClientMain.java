package org.example;

import org.example.client.Client;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) throws IOException {
        Client client = new Client();
        Scanner scn = new Scanner(System.in);
        while(true) {
            System.out.println("1) GET,   2) PUT");
            String command = scn.nextLine();
            switch (command){
                case "1":
                {
                    System.out.print("Enter Key:");
                    String key=scn.nextLine();
                    JSONObject json = client.sendCommand(key);


                    if(json.has("status") && json.get("status").equals("error")){
                        System.out.println("Missing key");
                        break;
                    }

                    if(json.get("close").equals("ok")){
                        System.out.println("You've timed out due to activity bye bye!");
                        client.close();
                        return;
                    }

                    System.out.println("Key: "+json.get("key")+" "+"\nValue: "+json.get("value"));
                    break;
                }
                case "2":
                {
                    System.out.print("Enter Key:");
                    String key=scn.nextLine();
                    System.out.print("Enter Value:");
                    String value=scn.nextLine();
                    JSONObject json =client.sendCommand(key,value);
                    if(json.get("close").equals("ok")){
                        System.out.println("You've timed out due to inactivity bye bye!");
                        client.close();
                        return;
                    }
                    break;
                }
                case "##":
                {
                    client.close();
                    return;
                }
            }

        }
    }
}
//mvn compile exec:java -Dexec.mainClass=org.example.ClientMain
//