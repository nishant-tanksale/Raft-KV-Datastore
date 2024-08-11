package kvraftstore;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.util.NetUtils;
import org.apache.ratis.statemachine.StateMachine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

public class Main {
    public static void main(String[] args) throws IOException {
        // Create Raft properties
        RaftProperties properties = new RaftProperties();
        Logger.getLogger("org.apache.ratis").setLevel(Level.ERROR);
        Logger.getLogger("org.apache.ratis.server.impl").setLevel(Level.ERROR);
        Logger.getLogger("org.apache.ratis.client.impl").setLevel(Level.ERROR);
        Logger.getLogger("org.apache.ratis.protocol").setLevel(Level.ERROR);
        Logger.getLogger("org.apache.ratis.util").setLevel(Level.ERROR);

        // Create a Raft group with three nodes
        String peerIdStr = System.getenv("PEER_ID");
        String peerPortStr = System.getenv("PEER_PORT");
        String dataDir = System.getenv("PEER_DATA_DIR");

        // Create Raft properties
        Logger.getLogger("org.apache.ratis").setLevel(Level.ERROR);

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

        // Create a Raft group with all the peers
        RaftGroupId groupId = RaftGroupId.valueOf(UUID.randomUUID());
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
    }
}
