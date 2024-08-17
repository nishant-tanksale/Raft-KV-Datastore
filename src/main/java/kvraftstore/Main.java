package kvraftstore;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.util.NetUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws IOException {
        // Create Raft properties
        RaftProperties properties = new RaftProperties();

        // Create a Raft group with three nodes
        String peerIdStr = System.getenv("PEER_ID");
        String peerPortStr = System.getenv("PEER_PORT");
        String dataDir = System.getenv("PEER_DATA_DIR");

        // Define the Raft peers
        List<RaftPeer> peers = new ArrayList<>();

        RaftPeerId peerId = RaftPeerId.valueOf(peerIdStr);
        RaftPeer peer = RaftPeer.newBuilder()
                .setId(peerId)
                .setAddress(NetUtils.createSocketAddr("localhost:" + peerPortStr))
                .build();
        peers.add(peer);

        // Read other peers' information from environment variables and add them to the list
        for (int i = 1; i <= 3; i++) {
            String otherPeerIdStr = System.getenv("PEER_" + i + "_ID");
            String otherPeerPortStr = System.getenv("PEER_" + i + "_PORT");
            if (otherPeerIdStr != null && !otherPeerIdStr.equals(peerIdStr)) {
                RaftPeerId otherPeerId = RaftPeerId.valueOf(otherPeerIdStr);
                RaftPeer otherPeer = RaftPeer.newBuilder()
                        .setId(otherPeerId)
                        .setAddress(NetUtils.createSocketAddr("localhost:" + otherPeerPortStr))
                        .build();
                peers.add(otherPeer);
            }
        }

        // Use a consistent RaftGroupId across all nodes
        RaftGroupId groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes("consistent-raft-group".getBytes()));
        RaftGroup raftGroup = RaftGroup.valueOf(groupId, peers);

        startRaftServer(peerId, raftGroup, properties, dataDir);
    }

    private static void startRaftServer(RaftPeerId peerId, RaftGroup raftGroup, RaftProperties properties, String dataDir) throws IOException {
        // Ensure the directory structure exists
        Path dir = Paths.get(dataDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            System.out.println("Created directory: " + dataDir);
        }

        // Create a StateMachine instance
        StateMachine stateMachine = KeyValueStateMachine.getInstance(dataDir, properties);

        // Create and start Raft server
        RaftServer server = RaftServer.newBuilder()
                .setGroup(raftGroup)
                .setServerId(peerId)
                .setStateMachine(stateMachine)
                .setProperties(properties)
                .build();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        System.out.println("Raft server started for peer: " + peerId + " on " + raftGroup.getPeer(peerId).getAddress());
        System.out.println("RaftGroupId: " + raftGroup.getGroupId());
        System.out.println("Raft cluster configuration:");
        for (RaftPeer peer : raftGroup.getPeers()) {
            System.out.println("Peer ID: " + peer.getId() + ", Address: " + peer.getAddress());
        }
    }
}
