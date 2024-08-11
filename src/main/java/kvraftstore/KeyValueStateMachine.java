package kvraftstore;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.protocol.RaftGroupId;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class KeyValueStateMachine extends BaseStateMachine {
    private static volatile KeyValueStateMachine instance;
    private static final Object LOCK = new Object();
    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();
    private RocksDB rocksDB;
    private final String rocksDBPath;

    private KeyValueStateMachine(String rocksDBPath, RaftProperties properties) {
        this.rocksDBPath = rocksDBPath;
        RocksDB.loadLibrary(); // Initialize RocksDB
    }

    public static KeyValueStateMachine getInstance(String rocksDBPath, RaftProperties properties) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new KeyValueStateMachine(rocksDBPath, properties);
                }
            }
        }
        return instance;
    }

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        this.storage.init(raftStorage);
        synchronized (LOCK) {
            if (rocksDB == null) {
                try {
                    Options options = new Options().setCreateIfMissing(true);
                    System.out.println("Thread " + Thread.currentThread().getName() + " is opening RocksDB at: " + rocksDBPath);
                    this.rocksDB = RocksDB.open(options, rocksDBPath);
                    System.out.println("RocksDB opened successfully.");
                } catch (RocksDBException e) {
                    System.err.println("Thread " + Thread.currentThread().getName() + " failed to initialize RocksDB: " + e.getMessage());
                    throw new IOException("Failed to initialize RocksDB", e);
                }
            }
        }
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public void close() {
        synchronized (LOCK) {
            if (rocksDB != null) {
                System.out.println("Closing RocksDB at: " + rocksDBPath);
                rocksDB.close();
                System.out.println("RocksDB closed.");
                rocksDB = null;
            }
            setLastAppliedTermIndex(null);
        }
    }


    @Override
    public TransactionContext startTransaction(RaftClientRequest request) {
        String command = request.getMessage().getContent().toStringUtf8();
        TransactionContext.Builder builder = TransactionContext.newBuilder()
                .setStateMachine(this)
                .setClientRequest(request)
                .setLogData(request.getMessage().getContent())
                .setStateMachineContext(command);
        return builder.build();
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        final String command = (String) trx.getStateMachineContext();
        final String[] parts = command.split(" ", 3);
        final String operation = parts[0].toLowerCase();
        final long index = trx.getLogEntry().getIndex();
        updateLastAppliedTermIndex(trx.getLogEntry().getTerm(), index);

        switch (operation) {
            case "put":
                return handlePut(parts[1], parts[2]);
            case "delete":
                return handleDelete(parts[1]);
            default:
                return CompletableFuture.completedFuture(Message.valueOf("Unknown operation"));
        }
    }

    private CompletableFuture<Message> handlePut(String key, String value) {
        try {
            rocksDB.put(key.getBytes(), value.getBytes());
            return CompletableFuture.completedFuture(Message.valueOf("Put successful"));
        } catch (RocksDBException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Message> handleDelete(String key) {
        try {
            rocksDB.delete(key.getBytes());
            return CompletableFuture.completedFuture(Message.valueOf("Delete successful"));
        } catch (RocksDBException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Message> query(Message request) {
        String key = request.getContent().toStringUtf8();
        byte[] value;
        try {
            value = rocksDB.get(key.getBytes());
            if (value == null) {
                return CompletableFuture.completedFuture(Message.valueOf("Key not found"));
            }
            return CompletableFuture.completedFuture(Message.valueOf(new String(value)));
        } catch (RocksDBException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
