package org.example.raft;

import org.example.message.MessageType;
import org.example.message.RequestMessage;
import org.example.server.state.NodeRole;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.example.server.state.NodeRole.candidate;
import static org.example.server.state.NodeRole.follower;

public class Beats implements Runnable{
    private Integer nodeId;
    private ArrayList<BlockingDeque<RequestMessage>> followers;
    private BlockingDeque<RequestMessage> leaderQueue;
    private final NodeRole nodeRole;
    private final ClusterRegistry registry = ClusterRegistry.getInstance();

    public Beats(NodeRole nodeRole, ArrayList<BlockingDeque<RequestMessage>> followers, Integer nodeId){

        this.nodeRole = nodeRole;
        this.followers=followers;
        this.nodeId=nodeId;
    }

    public Beats(NodeRole nodeRole, BlockingDeque<RequestMessage> leaderQueue,Integer nodeId){
        this.nodeRole = nodeRole;
        this.leaderQueue=leaderQueue;
        this.nodeId=nodeId;

    }

    public long getRandomTimeOutLeader(){

        return 150 + (long)(Math.random() * 150); // 150–300 ms

    }

    public long getRandomTimeOutFollower(){

        return 300 + (long)(Math.random() * 150); // 300–450 ms

    }
    public void sendBeats() throws InterruptedException {
        RequestMessage beat = new RequestMessage(MessageType.heartBeat);
        long time = getRandomTimeOutLeader();
        while(true){
            for(int i=0;i<4;i++){
                followers.get(i).putFirst(beat);
            }
            sleep(time);
        }
    }

    public void listenBeats() throws InterruptedException {
        long time=getRandomTimeOutFollower();
        while(true){
            RequestMessage beat=leaderQueue.poll(time, TimeUnit.MILLISECONDS);
            if(beat==null){
                registry.updateRole(nodeId,candidate);
                break;
            }
        }
    }
    @Override
    public void run() {
        if(nodeRole ==follower){
            try {
                listenBeats();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        else{
            try {
                sendBeats();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


    }
}
