package org.example.raft;

import org.example.message.RequestMessage;
import org.example.server.state.NodeRole;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class ClusterRegistry {
    private static final ClusterRegistry instance = new ClusterRegistry();
    private final ConcurrentHashMap<Integer, NodeRole> nodeRoles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, BlockingDeque<RequestMessage>> nodeInputQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, BlockingDeque<RequestMessage>> nodeBeatsQueues = new ConcurrentHashMap<>();


    private ClusterRegistry() {}

    public static ClusterRegistry getInstance() {
        return instance;
    }

    public void updateRole(int nodeId, NodeRole role) {
        BlockingDeque<RequestMessage> bdq = new LinkedBlockingDeque<>(100);
        BlockingDeque<RequestMessage> beat = new LinkedBlockingDeque<>(10);
        nodeBeatsQueues.put(nodeId,beat);
        nodeInputQueues.put(nodeId, bdq);
        nodeRoles.put(nodeId, role);
    }

    public void updateLeader(int leaderId) {
        for (Integer nodeID : nodeRoles.keySet()) {
            if (nodeRoles.get(nodeID) == NodeRole.leader) {
                BlockingDeque<RequestMessage> bdq = new LinkedBlockingDeque<>(100);
                BlockingDeque<RequestMessage> beat = new LinkedBlockingDeque<>(100);
                nodeBeatsQueues.put(nodeID,beat);
                nodeInputQueues.put(nodeID, bdq);
                nodeRoles.put(nodeID, NodeRole.follower);
            }
        }
        BlockingDeque<RequestMessage> bdq = new LinkedBlockingDeque<>(100);
        nodeInputQueues.put(leaderId, bdq);
        BlockingDeque<RequestMessage> beat = new LinkedBlockingDeque<>(100);
        nodeBeatsQueues.put(leaderId,beat);
        nodeRoles.put(leaderId, NodeRole.leader);
    }

    public Integer getLeaderId() {
        for (Integer nodeID : nodeRoles.keySet()) {
            if (nodeRoles.get(nodeID) == NodeRole.leader) {
                return nodeID;
            }
        }
        return null;
    }

    public ArrayList<Integer> getFollowerId() {
        ArrayList<Integer> followers = new ArrayList<>();
        for (Integer nodeID : nodeRoles.keySet()) {
            if (nodeRoles.get(nodeID) == NodeRole.follower) {
                followers.add(nodeID);
            }
        }
        return followers;
    }

    public NodeRole getRole(Integer nodeId) {
        return nodeRoles.get(nodeId);
    }

    public BlockingDeque<RequestMessage> getLeaderQueue() {
        Integer leaderId = getLeaderId();
        if (leaderId != null) {
            return nodeInputQueues.get(leaderId);
        }
        return null;
    }

    public BlockingDeque<RequestMessage> getFollowerQueue(int followerId) {
        if (nodeRoles.get(followerId) == NodeRole.follower) {
            return nodeInputQueues.get(followerId);
        }
        return null;
    }

    public ArrayList<BlockingDeque<RequestMessage>> getAllFollowerBeatsQueues() {
        ArrayList<BlockingDeque<RequestMessage>> queues = new ArrayList<>();
        for (Integer nodeId : getFollowerId()) {
            BlockingDeque<RequestMessage> q = nodeBeatsQueues.get(nodeId);
            if (q != null) queues.add(q);
        }
        return queues;
    }

    public BlockingDeque<RequestMessage> getLeaderBeatsQueue() {
        Integer leaderId = getLeaderId();
        if (leaderId != null) {
            return nodeBeatsQueues.get(leaderId);
        }
        return null;
    }

    public BlockingDeque<RequestMessage> getFollowerBeatsQueue(int followerId) {
        if (nodeRoles.get(followerId) == NodeRole.follower) {
            return nodeBeatsQueues.get(followerId);
        }
        return null;
    }

    public ArrayList<BlockingDeque<RequestMessage>> getAllFollowerQueues() {
        ArrayList<BlockingDeque<RequestMessage>> queues = new ArrayList<>();
        for (Integer nodeId : getFollowerId()) {
            BlockingDeque<RequestMessage> q = nodeInputQueues.get(nodeId);
            if (q != null) queues.add(q);
        }
        return queues;
    }
}
