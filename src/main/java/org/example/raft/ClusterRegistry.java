package org.example.raft;

import org.example.message.RequestMessage;
import org.example.server.state.NodeRole;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.Random;


/**
 * Registry of all nodes in the cluster, their roles, and their message queues.
 */
public class ClusterRegistry {
    private static final ClusterRegistry instance = new ClusterRegistry();

    // maps nodeId -> role
    private final ConcurrentHashMap<Integer, NodeRole> nodeRoles = new ConcurrentHashMap<>();

    // maps nodeId -> client request queue
    private final ConcurrentHashMap<Integer, BlockingDeque<RequestMessage>> nodeInputQueues = new ConcurrentHashMap<>();

    // maps nodeId -> heartbeat queue
    private final ConcurrentHashMap<Integer, BlockingDeque<RequestMessage>> nodeBeatsQueues = new ConcurrentHashMap<>();

    private ClusterRegistry() {
    }

    public static ClusterRegistry getInstance() {
        return instance;
    }

    /**
     * Update the role of a node. Create its queues only if this node is new.
     */
    public void updateRole(int nodeId, NodeRole role) {
        nodeRoles.computeIfAbsent(nodeId, id -> {
            nodeInputQueues.put(id, new LinkedBlockingDeque<RequestMessage>(100));
            nodeBeatsQueues.put(id, new LinkedBlockingDeque<RequestMessage>(100));
            return role;
        });
        nodeRoles.put(nodeId, role);
    }

    /**
     * Promote a node to leader, demoting any existing leader to follower.
     */
    public void updateLeader(int leaderId) {
        nodeInputQueues.computeIfAbsent(leaderId, id -> new LinkedBlockingDeque<RequestMessage>(100));
        nodeBeatsQueues.computeIfAbsent(leaderId, id -> new LinkedBlockingDeque<RequestMessage>(100));
        nodeRoles.put(leaderId, NodeRole.leader);
    }

    public Integer getLeaderId() {
        for (Map.Entry<Integer, NodeRole> entry : nodeRoles.entrySet()) {
            if (entry.getValue() == NodeRole.leader) {
                return entry.getKey();
            }
        }
        return null;
    }

    public ArrayList<Integer> getFollowerId() {
        ArrayList<Integer> followers = new ArrayList<>();
        for (Map.Entry<Integer, NodeRole> entry : nodeRoles.entrySet()) {
            if (entry.getValue() == NodeRole.follower) {
                followers.add(entry.getKey());
            }
        }
        return followers;
    }

    public NodeRole getRole(Integer nodeId) {
        return nodeRoles.get(nodeId);
    }

    public BlockingDeque<RequestMessage> getLeaderQueue() {
        Integer leader = getLeaderId();
        return leader != null ? nodeInputQueues.get(leader) : null;
    }

    public BlockingDeque<RequestMessage> getFollowerQueue(int followerId) {
        return (nodeRoles.get(followerId) == NodeRole.follower)
                ? nodeInputQueues.get(followerId)
                : null;
    }

    /**
     * Return queues for all peers (excluding the given nodeId).
     */
    public ArrayList<BlockingDeque<RequestMessage>> getAllPeersQueues(int selfId) {
        ArrayList<BlockingDeque<RequestMessage>> list = new ArrayList<>();
        for (Integer id : nodeRoles.keySet()) {
            if (id.equals(selfId)) continue;
            BlockingDeque<RequestMessage> q = nodeInputQueues.get(id);
            if (q != null) list.add(q);
        }
        return list;
    }

    /**
     * Return queues for all peers (including self).
     */

    public BlockingDeque<RequestMessage> getBeatsQueue(int nodeID) {
        return nodeBeatsQueues.get(nodeID);
    }

    /**
     * Return heartbeat queues for all peers (excluding the given nodeId).
     */
    public ArrayList<BlockingDeque<RequestMessage>> getAllPeersBeatsQueues(int selfId) {
        ArrayList<BlockingDeque<RequestMessage>> list = new ArrayList<>();
        for (Integer id : nodeRoles.keySet()) {
            if (id.equals(selfId)) continue;
            BlockingDeque<RequestMessage> q = nodeBeatsQueues.get(id);
            if (q != null) list.add(q);
        }
        return list;
    }

    public ArrayList<Integer> getAllPeersIds(int selfId) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (Integer id : nodeRoles.keySet()) {
            if (!id.equals(selfId)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public BlockingDeque<RequestMessage> getRandomFollowerQueue() {
        ArrayList<Integer> followerIds = new ArrayList<>();
        for (Map.Entry<Integer, NodeRole> entry : nodeRoles.entrySet()) {
            if (entry.getValue() == NodeRole.follower) {
                followerIds.add(entry.getKey());
            }
        }

        if (followerIds.isEmpty()) return null;

        Random random = new Random();
        int randomIndex = random.nextInt(followerIds.size());
        Integer followerId = followerIds.get(randomIndex);
        return nodeInputQueues.get(followerId);
    }

}
