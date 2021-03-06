/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.client;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Chore;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.Stoppable;
import org.apache.hadoop.hbase.client.MetaScanner.MetaScannerVisitor;
import org.apache.hadoop.hbase.client.MetaScanner.MetaScannerVisitorBase;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.exceptions.DoNotRetryIOException;
import org.apache.hadoop.hbase.exceptions.MasterNotRunningException;
import org.apache.hadoop.hbase.exceptions.RegionMovedException;
import org.apache.hadoop.hbase.exceptions.RegionOpeningException;
import org.apache.hadoop.hbase.exceptions.RegionServerStoppedException;
import org.apache.hadoop.hbase.exceptions.TableNotFoundException;
import org.apache.hadoop.hbase.exceptions.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.ipc.RpcClient;
import org.apache.hadoop.hbase.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.protobuf.RequestConverter;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos.AdminService;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.ClientService;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.CoprocessorServiceRequest;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.CoprocessorServiceResponse;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos.TableSchema;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.AddColumnRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.AddColumnResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.AssignRegionRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.AssignRegionResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.BalanceRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.BalanceResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.CatalogScanRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.CatalogScanResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.CreateTableRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.CreateTableResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DeleteColumnRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DeleteColumnResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DeleteSnapshotRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DeleteSnapshotResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DeleteTableRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DeleteTableResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DisableTableRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DisableTableResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DispatchMergingRegionsRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.DispatchMergingRegionsResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.EnableCatalogJanitorRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.EnableCatalogJanitorResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.EnableTableRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.EnableTableResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.IsCatalogJanitorEnabledRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.IsCatalogJanitorEnabledResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.IsRestoreSnapshotDoneRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.IsRestoreSnapshotDoneResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.IsSnapshotDoneRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.IsSnapshotDoneResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ListSnapshotRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ListSnapshotResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.MasterAdminService;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ModifyColumnRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ModifyColumnResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ModifyTableRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ModifyTableResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.MoveRegionRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.MoveRegionResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.OfflineRegionRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.OfflineRegionResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.RestoreSnapshotRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.RestoreSnapshotResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.SetBalancerRunningRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.SetBalancerRunningResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ShutdownRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.ShutdownResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.StopMasterRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.StopMasterResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.TakeSnapshotRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.TakeSnapshotResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.UnassignRegionRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterAdminProtos.UnassignRegionResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.GetClusterStatusRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.GetClusterStatusResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.GetSchemaAlterStatusRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.GetSchemaAlterStatusResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.GetTableDescriptorsRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.GetTableDescriptorsResponse;
import org.apache.hadoop.hbase.protobuf.generated.MasterMonitorProtos.MasterMonitorService;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos.IsMasterRunningRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos.IsMasterRunningResponse;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.SoftValueSortedMap;
import org.apache.hadoop.hbase.util.Triple;
import org.apache.hadoop.hbase.zookeeper.MasterAddressTracker;
import org.apache.hadoop.hbase.zookeeper.ZKUtil;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.zookeeper.KeeperException;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.BlockingRpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;

/**
 * A non-instantiable class that manages {@link HConnection}s.
 * This class has a static Map of {@link HConnection} instances keyed by
 * {@link Configuration}; all invocations of {@link #getConnection(Configuration)}
 * that pass the same {@link Configuration} instance will be returned the same
 * {@link  HConnection} instance (Adding properties to a Configuration
 * instance does not change its object identity; for more on how this is done see
 * {@link HConnectionKey}).  Sharing {@link HConnection}
 * instances is usually what you want; all clients of the {@link HConnection}
 * instances share the HConnections' cache of Region locations rather than each
 * having to discover for itself the location of meta, etc.  It makes
 * sense for the likes of the pool of HTables class {@link HTablePool}, for
 * instance (If concerned that a single {@link HConnection} is insufficient
 * for sharing amongst clients in say an heavily-multithreaded environment,
 * in practise its not proven to be an issue.  Besides, {@link HConnection} is
 * implemented atop Hadoop RPC and as of this writing, Hadoop RPC does a
 * connection per cluster-member, exclusively).
 *
 * <p>But sharing connections makes clean up of {@link HConnection} instances a little awkward.
 * Currently, clients cleanup by calling {@link #deleteConnection(Configuration)}. This will
 * shutdown the zookeeper connection the HConnection was using and clean up all
 * HConnection resources as well as stopping proxies to servers out on the
 * cluster. Not running the cleanup will not end the world; it'll
 * just stall the closeup some and spew some zookeeper connection failed
 * messages into the log.  Running the cleanup on a {@link HConnection} that is
 * subsequently used by another will cause breakage so be careful running
 * cleanup.
 * <p>To create a {@link HConnection} that is not shared by others, you can
 * create a new {@link Configuration} instance, pass this new instance to
 * {@link #getConnection(Configuration)}, and then when done, close it up by
 * doing something like the following:
 * <pre>
 * {@code
 * Configuration newConfig = new Configuration(originalConf);
 * HConnection connection = HConnectionManager.getConnection(newConfig);
 * // Use the connection to your hearts' delight and then when done...
 * HConnectionManager.deleteConnection(newConfig, true);
 * }
 * </pre>
 * <p>Cleanup used to be done inside in a shutdown hook.  On startup we'd
 * register a shutdown hook that called {@link #deleteAllConnections()}
 * on its way out but the order in which shutdown hooks run is not defined so
 * were problematic for clients of HConnection that wanted to register their
 * own shutdown hooks so we removed ours though this shifts the onus for
 * cleanup to the client.
 */
@SuppressWarnings("serial")
@InterfaceAudience.Public
@InterfaceStability.Evolving
public class HConnectionManager {
  static final Log LOG = LogFactory.getLog(HConnectionManager.class);

  public static final String RETRIES_BY_SERVER_KEY = "hbase.client.retries.by.server";

  // An LRU Map of HConnectionKey -> HConnection (TableServer).  All
  // access must be synchronized.  This map is not private because tests
  // need to be able to tinker with it.
  static final Map<HConnectionKey, HConnectionImplementation> CONNECTION_INSTANCES;

  public static final int MAX_CACHED_CONNECTION_INSTANCES;

  static {
    // We set instances to one more than the value specified for {@link
    // HConstants#ZOOKEEPER_MAX_CLIENT_CNXNS}. By default, the zk default max
    // connections to the ensemble from the one client is 30, so in that case we
    // should run into zk issues before the LRU hit this value of 31.
    MAX_CACHED_CONNECTION_INSTANCES = HBaseConfiguration.create().getInt(
      HConstants.ZOOKEEPER_MAX_CLIENT_CNXNS, HConstants.DEFAULT_ZOOKEPER_MAX_CLIENT_CNXNS) + 1;
    CONNECTION_INSTANCES = new LinkedHashMap<HConnectionKey, HConnectionImplementation>(
        (int) (MAX_CACHED_CONNECTION_INSTANCES / 0.75F) + 1, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(
          Map.Entry<HConnectionKey, HConnectionImplementation> eldest) {
         return size() > MAX_CACHED_CONNECTION_INSTANCES;
       }
    };
  }

  /*
   * Non-instantiable.
   */
  private HConnectionManager() {
    super();
  }

  /**
   * Get the connection that goes with the passed <code>conf</code> configuration instance.
   * If no current connection exists, method creates a new connection and keys it using
   * connection-specific properties from the passed {@link Configuration}; see
   * {@link HConnectionKey}.
   * @param conf configuration
   * @return HConnection object for <code>conf</code>
   * @throws ZooKeeperConnectionException
   */
  @SuppressWarnings("resource")
  public static HConnection getConnection(final Configuration conf)
  throws IOException {
    HConnectionKey connectionKey = new HConnectionKey(conf);
    synchronized (CONNECTION_INSTANCES) {
      HConnectionImplementation connection = CONNECTION_INSTANCES.get(connectionKey);
      if (connection == null) {
        connection = (HConnectionImplementation)createConnection(conf, true);
        CONNECTION_INSTANCES.put(connectionKey, connection);
      } else if (connection.isClosed()) {
        HConnectionManager.deleteConnection(connectionKey, true);
        connection = (HConnectionImplementation)createConnection(conf, true);
        CONNECTION_INSTANCES.put(connectionKey, connection);
      }
      connection.incCount();
      return connection;
    }
  }

  /**
   * Create a new HConnection instance using the passed <code>conf</code> instance.
   * <p>Note: This bypasses the usual HConnection life cycle management done by
   * {@link #getConnection(Configuration)}. Use this with caution, the caller is responsible for
   * calling {@link HConnection#close()} on the returned connection instance.
   * @param conf configuration
   * @return HConnection object for <code>conf</code>
   * @throws ZooKeeperConnectionException
   */
  public static HConnection createConnection(Configuration conf)
  throws IOException {
    return createConnection(conf, false);
  }

  static HConnection createConnection(final Configuration conf, final boolean managed)
  throws IOException {
    String className = conf.get("hbase.client.connection.impl",
      HConnectionManager.HConnectionImplementation.class.getName());
    Class<?> clazz = null;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
    try {
      // Default HCM#HCI is not accessible; make it so before invoking.
      Constructor<?> constructor =
        clazz.getDeclaredConstructor(Configuration.class, boolean.class);
      constructor.setAccessible(true);
      return (HConnection) constructor.newInstance(conf, managed);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * Delete connection information for the instance specified by passed configuration.
   * If there are no more references to the designated connection connection, this method will
   * then close connection to the zookeeper ensemble and let go of all associated resources.
   *
   * @param conf configuration whose identity is used to find {@link HConnection} instance.
   */
  public static void deleteConnection(Configuration conf) {
    deleteConnection(new HConnectionKey(conf), false);
  }

  /**
   * Cleanup a known stale connection.
   * This will then close connection to the zookeeper ensemble and let go of all resources.
   *
   * @param connection
   */
  public static void deleteStaleConnection(HConnection connection) {
    deleteConnection(connection, true);
  }

  /**
   * Delete information for all connections.
   */
  public static void deleteAllConnections() {
    synchronized (CONNECTION_INSTANCES) {
      Set<HConnectionKey> connectionKeys = new HashSet<HConnectionKey>();
      connectionKeys.addAll(CONNECTION_INSTANCES.keySet());
      for (HConnectionKey connectionKey : connectionKeys) {
        deleteConnection(connectionKey, false);
      }
      CONNECTION_INSTANCES.clear();
    }
  }

  private static void deleteConnection(HConnection connection, boolean staleConnection) {
    synchronized (CONNECTION_INSTANCES) {
      for (Entry<HConnectionKey, HConnectionImplementation> e: CONNECTION_INSTANCES.entrySet()) {
        if (e.getValue() == connection) {
          deleteConnection(e.getKey(), staleConnection);
          break;
        }
      }
    }
  }

  private static void deleteConnection(HConnectionKey connectionKey, boolean staleConnection) {
    synchronized (CONNECTION_INSTANCES) {
      HConnectionImplementation connection = CONNECTION_INSTANCES.get(connectionKey);
      if (connection != null) {
        connection.decCount();
        if (connection.isZeroReference() || staleConnection) {
          CONNECTION_INSTANCES.remove(connectionKey);
          connection.internalClose();
        }
      } else {
        LOG.error("Connection not found in the list, can't delete it "+
          "(connection key=" + connectionKey + "). May be the key was modified?");
      }
    }
  }

  /**
   * It is provided for unit test cases which verify the behavior of region
   * location cache prefetch.
   * @return Number of cached regions for the table.
   * @throws ZooKeeperConnectionException
   */
  static int getCachedRegionCount(Configuration conf, final byte[] tableName)
  throws IOException {
    return execute(new HConnectable<Integer>(conf) {
      @Override
      public Integer connect(HConnection connection) {
        return ((HConnectionImplementation)connection).getNumberOfCachedRegionLocations(tableName);
      }
    });
  }

  /**
   * It's provided for unit test cases which verify the behavior of region
   * location cache prefetch.
   * @return true if the region where the table and row reside is cached.
   * @throws ZooKeeperConnectionException
   */
  static boolean isRegionCached(Configuration conf, final byte[] tableName, final byte[] row)
  throws IOException {
    return execute(new HConnectable<Boolean>(conf) {
      @Override
      public Boolean connect(HConnection connection) {
        return ((HConnectionImplementation) connection).isRegionCached(tableName, row);
      }
    });
  }

  /**
   * This convenience method invokes the given {@link HConnectable#connect}
   * implementation using a {@link HConnection} instance that lasts just for the
   * duration of the invocation.
   *
   * @param <T> the return type of the connect method
   * @param connectable the {@link HConnectable} instance
   * @return the value returned by the connect method
   * @throws IOException
   */
  public static <T> T execute(HConnectable<T> connectable) throws IOException {
    if (connectable == null || connectable.conf == null) {
      return null;
    }
    Configuration conf = connectable.conf;
    HConnection connection = HConnectionManager.getConnection(conf);
    boolean connectSucceeded = false;
    try {
      T returnValue = connectable.connect(connection);
      connectSucceeded = true;
      return returnValue;
    } finally {
      try {
        connection.close();
      } catch (Exception e) {
        if (connectSucceeded) {
          throw new IOException("The connection to " + connection
              + " could not be deleted.", e);
        }
      }
    }
  }

  /** Encapsulates connection to zookeeper and regionservers.*/
  @edu.umd.cs.findbugs.annotations.SuppressWarnings(
      value="AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION",
      justification="Access to the conncurrent hash map is under a lock so should be fine.")
  static class HConnectionImplementation implements HConnection, Closeable {
    static final Log LOG = LogFactory.getLog(HConnectionImplementation.class);
    private final long pause;
    private final int numTries;
    final int rpcTimeout;
    private final int prefetchRegionLimit;
    private final boolean useServerTrackerForRetries;
    private final long serverTrackerTimeout;

    private volatile boolean closed;
    private volatile boolean aborted;

    // package protected for the tests
    ClusterStatusListener clusterStatusListener;

    private final Object userRegionLock = new Object();

    // We have a single lock for master & zk to prevent deadlocks. Having
    //  one lock for ZK and one lock for master is not possible:
    //  When creating a connection to master, we need a connection to ZK to get
    //  its address. But another thread could have taken the ZK lock, and could
    //  be waiting for the master lock => deadlock.
    private final Object masterAndZKLock = new Object();

    private long keepZooKeeperWatcherAliveUntil = Long.MAX_VALUE;
    private final DelayedClosing delayedClosing =
      DelayedClosing.createAndStart(this);


    private final Configuration conf;

    // Client rpc instance.
    private RpcClient rpcClient;

    /**
     * Map of table to table {@link HRegionLocation}s.  The table key is made
     * by doing a {@link Bytes#mapKey(byte[])} of the table's name.
     */
    private final Map<Integer, SoftValueSortedMap<byte [], HRegionLocation>> cachedRegionLocations =
      new HashMap<Integer, SoftValueSortedMap<byte [], HRegionLocation>>();

    // The presence of a server in the map implies it's likely that there is an
    // entry in cachedRegionLocations that map to this server; but the absence
    // of a server in this map guarentees that there is no entry in cache that
    // maps to the absent server.
    // The access to this attribute must be protected by a lock on cachedRegionLocations
    private final Set<ServerName> cachedServers = new HashSet<ServerName>();

    // region cache prefetch is enabled by default. this set contains all
    // tables whose region cache prefetch are disabled.
    private final Set<Integer> regionCachePrefetchDisabledTables =
      new CopyOnWriteArraySet<Integer>();

    private int refCount;

    // indicates whether this connection's life cycle is managed (by us)
    private final boolean managed;

    /**
     * Cluster registry of basic info such as clusterid and meta region location.
     */
    final Registry registry;

    /**
     * constructor
     * @param conf Configuration object
     * @param managed If true, does not do full shutdown on close; i.e. cleanup of connection
     * to zk and shutdown of all services; we just close down the resources this connection was
     * responsible for and decrement usage counters.  It is up to the caller to do the full
     * cleanup.  It is set when we want have connection sharing going on -- reuse of zk connection,
     * and cached region locations, established regionserver connections, etc.  When connections
     * are shared, we have reference counting going on and will only do full cleanup when no more
     * users of an HConnectionImplementation instance.
     */
    HConnectionImplementation(Configuration conf, boolean managed) throws IOException {
      this.conf = conf;
      this.managed = managed;
      this.closed = false;
      this.pause = conf.getLong(HConstants.HBASE_CLIENT_PAUSE,
        HConstants.DEFAULT_HBASE_CLIENT_PAUSE);
      this.numTries = conf.getInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER,
        HConstants.DEFAULT_HBASE_CLIENT_RETRIES_NUMBER);
      this.rpcTimeout = conf.getInt(
        HConstants.HBASE_RPC_TIMEOUT_KEY,
        HConstants.DEFAULT_HBASE_RPC_TIMEOUT);
      this.prefetchRegionLimit = conf.getInt(
        HConstants.HBASE_CLIENT_PREFETCH_LIMIT,
        HConstants.DEFAULT_HBASE_CLIENT_PREFETCH_LIMIT);
      this.useServerTrackerForRetries = conf.getBoolean(RETRIES_BY_SERVER_KEY, true);
      long serverTrackerTimeout = 0;
      if (this.useServerTrackerForRetries) {
        // Server tracker allows us to do faster, and yet useful (hopefully), retries.
        // However, if we are too useful, we might fail very quickly due to retry count limit.
        // To avoid this, we are going to cheat for now (see HBASE-7659), and calculate maximum
        // retry time if normal retries were used. Then we will retry until this time runs out.
        // If we keep hitting one server, the net effect will be the incremental backoff, and
        // essentially the same number of retries as planned. If we have to do faster retries,
        // we will do more retries in aggregate, but the user will be none the wiser.
        for (int i = 0; i < this.numTries; ++i) {
          serverTrackerTimeout += ConnectionUtils.getPauseTime(this.pause, i);
        }
      }
      this.serverTrackerTimeout = serverTrackerTimeout;
      this.registry = setupRegistry();
      retrieveClusterId();

      this.rpcClient = new RpcClient(this.conf, this.clusterId);

      // Do we publish the status?
      Class<? extends ClusterStatusListener.Listener> listenerClass =
          conf.getClass(ClusterStatusListener.STATUS_LISTENER_CLASS,
              ClusterStatusListener.DEFAULT_STATUS_LISTENER_CLASS,
              ClusterStatusListener.Listener.class);

      if (listenerClass != null) {
        clusterStatusListener = new ClusterStatusListener(
            new ClusterStatusListener.DeadServerHandler() {
              @Override
              public void newDead(ServerName sn) {
                clearCaches(sn);
                rpcClient.cancelConnections(sn.getHostname(), sn.getPort(),
                  new SocketException(sn.getServerName() + " is dead: closing its connection."));
              }
            }, conf, listenerClass);
      }
    }
 
    /**
     * @return The cluster registry implementation to use.
     * @throws IOException
     */
    private Registry setupRegistry() throws IOException {
      String registryClass = this.conf.get("hbase.client.registry.impl",
        ZooKeeperRegistry.class.getName());
      Registry registry = null;
      try {
        registry = (Registry)Class.forName(registryClass).newInstance();
      } catch (Throwable t) {
        throw new IOException(t);
      }
      registry.init(this);
      return registry;
    }

    /**
     * For tests only.
     * @param rpcClient Client we should use instead.
     * @return Previous rpcClient
     */
    RpcClient setRpcClient(final RpcClient rpcClient) {
      RpcClient oldRpcClient = this.rpcClient;
      this.rpcClient = rpcClient;
      return oldRpcClient;
    }

    /**
     * An identifier that will remain the same for a given connection.
     * @return
     */
    public String toString(){
      return "hconnection-0x" + Integer.toHexString(hashCode());
    }

    protected String clusterId = null;

    void retrieveClusterId() {
      if (clusterId != null) return;
      this.clusterId = this.registry.getClusterId();
      if (clusterId == null) {
        clusterId = HConstants.CLUSTER_ID_DEFAULT;
        LOG.debug("clusterid came back null, using default " + clusterId);
      }
    }

    @Override
    public Configuration getConfiguration() {
      return this.conf;
    }

    private void checkIfBaseNodeAvailable(ZooKeeperWatcher zkw)
      throws MasterNotRunningException {
      String errorMsg;
      try {
        if (ZKUtil.checkExists(zkw, zkw.baseZNode) == -1) {
          errorMsg = "The node " + zkw.baseZNode+" is not in ZooKeeper. "
            + "It should have been written by the master. "
            + "Check the value configured in 'zookeeper.znode.parent'. "
            + "There could be a mismatch with the one configured in the master.";
          LOG.error(errorMsg);
          throw new MasterNotRunningException(errorMsg);
        }
      } catch (KeeperException e) {
        errorMsg = "Can't get connection to ZooKeeper: " + e.getMessage();
        LOG.error(errorMsg);
        throw new MasterNotRunningException(errorMsg, e);
      }
    }

    /**
     * @return true if the master is running, throws an exception otherwise
     * @throws MasterNotRunningException - if the master is not running
     * @throws ZooKeeperConnectionException
     */
    @Override
    public boolean isMasterRunning()
    throws MasterNotRunningException, ZooKeeperConnectionException {
      // When getting the master connection, we check it's running,
      // so if there is no exception, it means we've been able to get a
      // connection on a running master
      MasterMonitorKeepAliveConnection m = getKeepAliveMasterMonitorService();
      try {
        m.close();
      } catch (IOException e) {
        throw new MasterNotRunningException("Failed close", e);
      }
      return true;
    }

    @Override
    public HRegionLocation getRegionLocation(final byte [] name,
        final byte [] row, boolean reload)
    throws IOException {
      return reload? relocateRegion(name, row): locateRegion(name, row);
    }

    @Override
    public boolean isTableEnabled(byte[] tableName) throws IOException {
      return this.registry.isTableOnlineState(tableName, true);
    }

    @Override
    public boolean isTableDisabled(byte[] tableName) throws IOException {
      return this.registry.isTableOnlineState(tableName, false);
    }

    @Override
    public boolean isTableAvailable(final byte[] tableName) throws IOException {
      final AtomicBoolean available = new AtomicBoolean(true);
      final AtomicInteger regionCount = new AtomicInteger(0);
      MetaScannerVisitor visitor = new MetaScannerVisitorBase() {
        @Override
        public boolean processRow(Result row) throws IOException {
          HRegionInfo info = MetaScanner.getHRegionInfo(row);
          if (info != null) {
            if (Bytes.compareTo(tableName, info.getTableName()) == 0) {
              ServerName server = HRegionInfo.getServerName(row);
              if (server == null) {
                available.set(false);
                return false;
              }
              regionCount.incrementAndGet();
            } else if (Bytes.compareTo(tableName, info.getTableName()) < 0) {
              // Return if we are done with the current table
              return false;
            }
          }
          return true;
        }
      };
      MetaScanner.metaScan(conf, visitor, tableName);
      return available.get() && (regionCount.get() > 0);
    }

    @Override
    public boolean isTableAvailable(final byte[] tableName, final byte[][] splitKeys)
        throws IOException {
      final AtomicBoolean available = new AtomicBoolean(true);
      final AtomicInteger regionCount = new AtomicInteger(0);
      MetaScannerVisitor visitor = new MetaScannerVisitorBase() {
        @Override
        public boolean processRow(Result row) throws IOException {
          HRegionInfo info = MetaScanner.getHRegionInfo(row);
          if (info != null) {
            if (Bytes.compareTo(tableName, info.getTableName()) == 0) {
              ServerName server = HRegionInfo.getServerName(row);
              if (server == null) {
                available.set(false);
                return false;
              }
              if (!Bytes.equals(info.getStartKey(), HConstants.EMPTY_BYTE_ARRAY)) {
                for (byte[] splitKey : splitKeys) {
                  // Just check if the splitkey is available
                  if (Bytes.equals(info.getStartKey(), splitKey)) {
                    regionCount.incrementAndGet();
                    break;
                  }
                }
              } else {
                // Always empty start row should be counted
                regionCount.incrementAndGet();
              }
            } else if (Bytes.compareTo(tableName, info.getTableName()) < 0) {
              // Return if we are done with the current table
              return false;
            }
          }
          return true;
        }
      };
      MetaScanner.metaScan(conf, visitor, tableName);
      // +1 needs to be added so that the empty start row is also taken into account
      return available.get() && (regionCount.get() == splitKeys.length + 1);
    }

    @Override
    public HRegionLocation locateRegion(final byte[] regionName) throws IOException {
      return locateRegion(HRegionInfo.getTableName(regionName),
          HRegionInfo.getStartKey(regionName), false, true);
    }

    @Override
    public boolean isDeadServer(ServerName sn) {
      if (clusterStatusListener == null) {
        return false;
      } else {
        return clusterStatusListener.isDeadServer(sn);
      }
    }

    @Override
    public List<HRegionLocation> locateRegions(final byte[] tableName)
    throws IOException {
      return locateRegions (tableName, false, true);
    }

    @Override
    public List<HRegionLocation> locateRegions(final byte[] tableName, final boolean useCache,
        final boolean offlined) throws IOException {
      NavigableMap<HRegionInfo, ServerName> regions = MetaScanner.allTableRegions(conf, tableName,
        offlined);
      final List<HRegionLocation> locations = new ArrayList<HRegionLocation>();
      for (HRegionInfo regionInfo : regions.keySet()) {
        locations.add(locateRegion(tableName, regionInfo.getStartKey(), useCache, true));
      }
      return locations;
    }

    @Override
    public HRegionLocation locateRegion(final byte [] tableName,
        final byte [] row)
    throws IOException{
      return locateRegion(tableName, row, true, true);
    }

    @Override
    public HRegionLocation relocateRegion(final byte [] tableName,
        final byte [] row)
    throws IOException{

      // Since this is an explicit request not to use any caching, finding
      // disabled tables should not be desirable.  This will ensure that an exception is thrown when
      // the first time a disabled table is interacted with.
      if (isTableDisabled(tableName)) {
        throw new DoNotRetryIOException(Bytes.toString(tableName) + " is disabled.");
      }

      return locateRegion(tableName, row, false, true);
    }

    private HRegionLocation locateRegion(final byte [] tableName,
      final byte [] row, boolean useCache, boolean retry)
    throws IOException {
      if (this.closed) throw new IOException(toString() + " closed");
      if (tableName == null || tableName.length == 0) {
        throw new IllegalArgumentException(
            "table name cannot be null or zero length");
      }

      if (Bytes.equals(tableName, HConstants.META_TABLE_NAME)) {
        return this.registry.getMetaRegionLocation();
      } else {
        // Region not in the cache - have to go to the meta RS
        return locateRegionInMeta(HConstants.META_TABLE_NAME, tableName, row,
          useCache, userRegionLock, retry);
      }
    }

    /*
     * Search .META. for the HRegionLocation info that contains the table and
     * row we're seeking. It will prefetch certain number of regions info and
     * save them to the global region cache.
     */
    private void prefetchRegionCache(final byte[] tableName,
        final byte[] row) {
      // Implement a new visitor for MetaScanner, and use it to walk through
      // the .META.
      MetaScannerVisitor visitor = new MetaScannerVisitorBase() {
        public boolean processRow(Result result) throws IOException {
          try {
            HRegionInfo regionInfo = MetaScanner.getHRegionInfo(result);
            if (regionInfo == null) {
              return true;
            }

            // possible we got a region of a different table...
            if (!Bytes.equals(regionInfo.getTableName(), tableName)) {
              return false; // stop scanning
            }
            if (regionInfo.isOffline()) {
              // don't cache offline regions
              return true;
            }

            ServerName serverName = HRegionInfo.getServerName(result);
            if (serverName == null) {
              return true; // don't cache it
            }
            // instantiate the location
            long seqNum = HRegionInfo.getSeqNumDuringOpen(result);
            HRegionLocation loc = new HRegionLocation(regionInfo, serverName, seqNum);
            // cache this meta entry
            cacheLocation(tableName, null, loc);
            return true;
          } catch (RuntimeException e) {
            throw new IOException(e);
          }
        }
      };
      try {
        // pre-fetch certain number of regions info at region cache.
        MetaScanner.metaScan(conf, visitor, tableName, row,
            this.prefetchRegionLimit);
      } catch (IOException e) {
        LOG.warn("Encountered problems when prefetch META table: ", e);
      }
    }

    /*
      * Search the .META. table for the HRegionLocation
      * info that contains the table and row we're seeking.
      */
    private HRegionLocation locateRegionInMeta(final byte [] parentTable,
      final byte [] tableName, final byte [] row, boolean useCache,
      Object regionLockObject, boolean retry)
    throws IOException {
      HRegionLocation location;
      // If we are supposed to be using the cache, look in the cache to see if
      // we already have the region.
      if (useCache) {
        location = getCachedLocation(tableName, row);
        if (location != null) {
          return location;
        }
      }
      int localNumRetries = retry ? numTries : 1;
      // build the key of the meta region we should be looking for.
      // the extra 9's on the end are necessary to allow "exact" matches
      // without knowing the precise region names.
      byte [] metaKey = HRegionInfo.createRegionName(tableName, row,
        HConstants.NINES, false);
      for (int tries = 0; true; tries++) {
        if (tries >= localNumRetries) {
          throw new NoServerForRegionException("Unable to find region for "
            + Bytes.toStringBinary(row) + " after " + numTries + " tries.");
        }

        HRegionLocation metaLocation = null;
        try {
          // locate the meta region
          metaLocation = locateRegion(parentTable, metaKey, true, false);
          // If null still, go around again.
          if (metaLocation == null) continue;
          ClientService.BlockingInterface service = getClient(metaLocation.getServerName());

          Result regionInfoRow;
          // This block guards against two threads trying to load the meta
          // region at the same time. The first will load the meta region and
          // the second will use the value that the first one found.
          synchronized (regionLockObject) {
            // Check the cache again for a hit in case some other thread made the
            // same query while we were waiting on the lock. 
            if (useCache) {
              location = getCachedLocation(tableName, row);
              if (location != null) {
                return location;
              }
              // If the parent table is META, we may want to pre-fetch some
              // region info into the global region cache for this table.
              if (Bytes.equals(parentTable, HConstants.META_TABLE_NAME)
                  && (getRegionCachePrefetch(tableName))) {
                prefetchRegionCache(tableName, row);
              }
              location = getCachedLocation(tableName, row);
              if (location != null) {
                return location;
              }
            } else {
              // If we are not supposed to be using the cache, delete any existing cached location
              // so it won't interfere.
              forceDeleteCachedLocation(tableName, row);
            }
            // Query the meta region for the location of the meta region
            regionInfoRow = ProtobufUtil.getRowOrBefore(service,
              metaLocation.getRegionInfo().getRegionName(), metaKey,
              HConstants.CATALOG_FAMILY);
          }
          if (regionInfoRow == null) {
            throw new TableNotFoundException(Bytes.toString(tableName));
          }

          // convert the row result into the HRegionLocation we need!
          HRegionInfo regionInfo = MetaScanner.getHRegionInfo(regionInfoRow);
          if (regionInfo == null) {
            throw new IOException("HRegionInfo was null or empty in " +
              Bytes.toString(parentTable) + ", row=" + regionInfoRow);
          }

          // possible we got a region of a different table...
          if (!Bytes.equals(regionInfo.getTableName(), tableName)) {
            throw new TableNotFoundException(
                  "Table '" + Bytes.toString(tableName) + "' was not found, got: " +
                  Bytes.toString(regionInfo.getTableName()) + ".");
          }
          if (regionInfo.isSplit()) {
            throw new RegionOfflineException("the only available region for" +
              " the required row is a split parent," +
              " the daughters should be online soon: " +
              regionInfo.getRegionNameAsString());
          }
          if (regionInfo.isOffline()) {
            throw new RegionOfflineException("the region is offline, could" +
              " be caused by a disable table call: " +
              regionInfo.getRegionNameAsString());
          }

          ServerName serverName = HRegionInfo.getServerName(regionInfoRow);
          if (serverName == null) {
            throw new NoServerForRegionException("No server address listed " +
              "in " + Bytes.toString(parentTable) + " for region " +
              regionInfo.getRegionNameAsString() + " containing row " +
              Bytes.toStringBinary(row));
          }

          if (isDeadServer(serverName)){
            throw new RegionServerStoppedException(".META. says the region "+
                regionInfo.getRegionNameAsString()+" is managed by the server " + serverName +
                ", but it is dead.");
          }

          // Instantiate the location
          location = new HRegionLocation(regionInfo, serverName,
            HRegionInfo.getSeqNumDuringOpen(regionInfoRow));
          cacheLocation(tableName, null, location);
          return location;
        } catch (TableNotFoundException e) {
          // if we got this error, probably means the table just plain doesn't
          // exist. rethrow the error immediately. this should always be coming
          // from the HTable constructor.
          throw e;
        } catch (IOException e) {
          if (e instanceof RemoteException) {
            e = ((RemoteException)e).unwrapRemoteException();
          }
          if (tries < numTries - 1) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("locateRegionInMeta parentTable=" +
                Bytes.toString(parentTable) + ", metaLocation=" +
                ((metaLocation == null)? "null": "{" + metaLocation + "}") +
                ", attempt=" + tries + " of " +
                this.numTries + " failed; retrying after sleep of " +
                ConnectionUtils.getPauseTime(this.pause, tries) + " because: " + e.getMessage());
            }
          } else {
            throw e;
          }
          // Only relocate the parent region if necessary
          if(!(e instanceof RegionOfflineException ||
              e instanceof NoServerForRegionException)) {
            relocateRegion(parentTable, metaKey);
          }
        }
        try{
          Thread.sleep(ConnectionUtils.getPauseTime(this.pause, tries));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Giving up trying to location region in " +
            "meta: thread is interrupted.");
        }
      }
    }

    /*
     * Search the cache for a location that fits our table and row key.
     * Return null if no suitable region is located. TODO: synchronization note
     *
     * <p>TODO: This method during writing consumes 15% of CPU doing lookup
     * into the Soft Reference SortedMap.  Improve.
     *
     * @param tableName
     * @param row
     * @return Null or region location found in cache.
     */
    HRegionLocation getCachedLocation(final byte [] tableName,
        final byte [] row) {
      SoftValueSortedMap<byte [], HRegionLocation> tableLocations =
        getTableLocations(tableName);

      // start to examine the cache. we can only do cache actions
      // if there's something in the cache for this table.
      if (tableLocations.isEmpty()) {
        return null;
      }

      HRegionLocation possibleRegion = tableLocations.get(row);
      if (possibleRegion != null) {
        return possibleRegion;
      }

      possibleRegion = tableLocations.lowerValueByKey(row);
      if (possibleRegion == null) {
        return null;
      }

      // make sure that the end key is greater than the row we're looking
      // for, otherwise the row actually belongs in the next region, not
      // this one. the exception case is when the endkey is
      // HConstants.EMPTY_END_ROW, signifying that the region we're
      // checking is actually the last region in the table.
      byte[] endKey = possibleRegion.getRegionInfo().getEndKey();
      if (Bytes.equals(endKey, HConstants.EMPTY_END_ROW) ||
          KeyValue.getRowComparator(tableName).compareRows(
              endKey, 0, endKey.length, row, 0, row.length) > 0) {
        return possibleRegion;
      }

      // Passed all the way through, so we got nothing - complete cache miss
      return null;
    }

    /**
     * Delete a cached location, no matter what it is. Called when we were told to not use cache.
     * @param tableName tableName
     * @param row
     */
    void forceDeleteCachedLocation(final byte [] tableName, final byte [] row) {
      HRegionLocation rl = null;
      synchronized (this.cachedRegionLocations) {
        Map<byte[], HRegionLocation> tableLocations = getTableLocations(tableName);
        // start to examine the cache. we can only do cache actions
        // if there's something in the cache for this table.
        if (!tableLocations.isEmpty()) {
          rl = getCachedLocation(tableName, row);
          if (rl != null) {
            tableLocations.remove(rl.getRegionInfo().getStartKey());
          }
        }
      }
      if ((rl != null) && LOG.isDebugEnabled()) {
        LOG.debug("Removed " + rl.getHostname() + ":" + rl.getPort()
          + " as a location of " + rl.getRegionInfo().getRegionNameAsString() +
          " for tableName=" + Bytes.toString(tableName) + " from cache");
      }
    }

    /*
     * Delete all cached entries of a table that maps to a specific location.
     */
    @Override
    public void clearCaches(final ServerName serverName){
      boolean deletedSomething = false;
      synchronized (this.cachedRegionLocations) {
        if (!cachedServers.contains(serverName)) {
          return;
        }
        for (Map<byte[], HRegionLocation> tableLocations :
            cachedRegionLocations.values()) {
          for (Entry<byte[], HRegionLocation> e : tableLocations.entrySet()) {
            if (serverName.equals(e.getValue().getServerName())) {
              tableLocations.remove(e.getKey());
              deletedSomething = true;
            }
          }
        }
        cachedServers.remove(serverName);
      }
      if (deletedSomething && LOG.isDebugEnabled()) {
        LOG.debug("Removed all cached region locations that map to " + serverName);
      }
    }

    /*
     * @param tableName
     * @return Map of cached locations for passed <code>tableName</code>
     */
    private SoftValueSortedMap<byte [], HRegionLocation> getTableLocations(
        final byte [] tableName) {
      // find the map of cached locations for this table
      Integer key = Bytes.mapKey(tableName);
      SoftValueSortedMap<byte [], HRegionLocation> result;
      synchronized (this.cachedRegionLocations) {
        result = this.cachedRegionLocations.get(key);
        // if tableLocations for this table isn't built yet, make one
        if (result == null) {
          result = new SoftValueSortedMap<byte [], HRegionLocation>(
              Bytes.BYTES_COMPARATOR);
          this.cachedRegionLocations.put(key, result);
        }
      }
      return result;
    }

    @Override
    public void clearRegionCache() {
      synchronized(this.cachedRegionLocations) {
        this.cachedRegionLocations.clear();
        this.cachedServers.clear();
      }
    }

    @Override
    public void clearRegionCache(final byte [] tableName) {
      synchronized (this.cachedRegionLocations) {
        this.cachedRegionLocations.remove(Bytes.mapKey(tableName));
      }
    }

    /**
     * Put a newly discovered HRegionLocation into the cache.
     * @param tableName The table name.
     * @param source the source of the new location, if it's not coming from meta
     * @param location the new location
     */
    private void cacheLocation(final byte [] tableName, final HRegionLocation source,
        final HRegionLocation location) {
      boolean isFromMeta = (source == null);
      byte [] startKey = location.getRegionInfo().getStartKey();
      Map<byte [], HRegionLocation> tableLocations =
        getTableLocations(tableName);
      boolean isNewCacheEntry = false;
      boolean isStaleUpdate = false;
      HRegionLocation oldLocation = null;
      synchronized (this.cachedRegionLocations) {
        cachedServers.add(location.getServerName());
        oldLocation = tableLocations.get(startKey);
        isNewCacheEntry = (oldLocation == null);
        // If the server in cache sends us a redirect, assume it's always valid.
        if (!isNewCacheEntry && !oldLocation.equals(source)) {
          long newLocationSeqNum = location.getSeqNum();
          // Meta record is stale - some (probably the same) server has closed the region
          // with later seqNum and told us about the new location.
          boolean isStaleMetaRecord = isFromMeta && (oldLocation.getSeqNum() > newLocationSeqNum);
          // Same as above for redirect. However, in this case, if the number is equal to previous
          // record, the most common case is that first the region was closed with seqNum, and then
          // opened with the same seqNum; hence we will ignore the redirect.
          // There are so many corner cases with various combinations of opens and closes that
          // an additional counter on top of seqNum would be necessary to handle them all.
          boolean isStaleRedirect = !isFromMeta && (oldLocation.getSeqNum() >= newLocationSeqNum);
          isStaleUpdate = (isStaleMetaRecord || isStaleRedirect);
        }
        if (!isStaleUpdate) {
          tableLocations.put(startKey, location);
        }
      }
      if (isNewCacheEntry) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Cached location for " +
            location.getRegionInfo().getRegionNameAsString() +
            " is " + location.getHostnamePort());
        }
      } else if (isStaleUpdate && !location.equals(oldLocation)) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Ignoring stale location update for "
            + location.getRegionInfo().getRegionNameAsString() + ": "
            + location.getHostnamePort() + " at " + location.getSeqNum() + "; local "
            + oldLocation.getHostnamePort() + " at " + oldLocation.getSeqNum());
        }
      }
    }

    // Map keyed by service name + regionserver to service stub implementation
    private final ConcurrentHashMap<String, Object> stubs =
      new ConcurrentHashMap<String, Object>();
    // Map of locks used creating service stubs per regionserver.
    private final ConcurrentHashMap<String, String> connectionLock =
      new ConcurrentHashMap<String, String>();

    /**
     * Maintains current state of MasterService instance.
     */
    static abstract class MasterServiceState {
      HConnection connection;
      int userCount;
      long keepAliveUntil = Long.MAX_VALUE;

      MasterServiceState (final HConnection connection) {
        super();
        this.connection = connection;
      }

      abstract Object getStub();
      abstract void clearStub();
      abstract boolean isMasterRunning() throws ServiceException;
    }

    /**
     * State of the MasterAdminService connection/setup.
     */
    static class MasterAdminServiceState extends MasterServiceState {
      MasterAdminService.BlockingInterface stub;
      MasterAdminServiceState(final HConnection connection) {
        super(connection);
      }

      @Override
      public String toString() {
        return "MasterAdminService";
      }

      @Override
      Object getStub() {
        return this.stub;
      }

      @Override
      void clearStub() {
        this.stub = null;
      }

      @Override
      boolean isMasterRunning() throws ServiceException {
        MasterProtos.IsMasterRunningResponse response =
          this.stub.isMasterRunning(null, RequestConverter.buildIsMasterRunningRequest());
        return response != null? response.getIsMasterRunning(): false;
      }
    }

    /**
     * State of the MasterMonitorService connection/setup.
     */
    static class MasterMonitorServiceState extends MasterServiceState {
      MasterMonitorService.BlockingInterface stub;
      MasterMonitorServiceState(final HConnection connection) {
        super(connection);
      }

      @Override
      public String toString() {
        return "MasterMonitorService";
      }

      @Override
      Object getStub() {
        return this.stub;
      }

      @Override
      void clearStub() {
        this.stub = null;
      }

      @Override
      boolean isMasterRunning() throws ServiceException {
        MasterProtos.IsMasterRunningResponse response =
          this.stub.isMasterRunning(null, RequestConverter.buildIsMasterRunningRequest());
        return response != null? response.getIsMasterRunning(): false;
      }
    }

    /**
     * Makes a client-side stub for master services. Sub-class to specialize.
     * Depends on hosting class so not static.  Exists so we avoid duplicating a bunch of code
     * when setting up the MasterMonitorService and MasterAdminService.
     */
    abstract class StubMaker {
      /**
       * Returns the name of the service stub being created.
       */
      protected abstract String getServiceName();

      /**
       * Make stub and cache it internal so can be used later doing the isMasterRunning call.
       * @param channel
       */
      protected abstract Object makeStub(final BlockingRpcChannel channel);

      /**
       * Once setup, check it works by doing isMasterRunning check.
       * @throws ServiceException
       */
      protected abstract void isMasterRunning() throws ServiceException;

      /**
       * Create a stub. Try once only.  It is not typed because there is no common type to
       * protobuf services nor their interfaces.  Let the caller do appropriate casting.
       * @return A stub for master services.
       * @throws IOException
       * @throws KeeperException
       * @throws ServiceException
       */
      private Object makeStubNoRetries() throws IOException, KeeperException, ServiceException {
        ZooKeeperKeepAliveConnection zkw;
        try {
          zkw = getKeepAliveZooKeeperWatcher();
        } catch (IOException e) {
          throw new ZooKeeperConnectionException("Can't connect to ZooKeeper", e);
        }
        try {
          checkIfBaseNodeAvailable(zkw);
          ServerName sn = MasterAddressTracker.getMasterAddress(zkw);
          if (sn == null) {
            String msg = "ZooKeeper available but no active master location found";
            LOG.info(msg);
            throw new MasterNotRunningException(msg);
          }
          if (isDeadServer(sn)) {
            throw new MasterNotRunningException(sn + " is dead.");
          }
          // Use the security info interface name as our stub key
          String key = getStubKey(getServiceName(), sn.getHostAndPort());
          connectionLock.putIfAbsent(key, key);
          Object stub = null;
          synchronized (connectionLock.get(key)) {
            stub = stubs.get(key);
            if (stub == null) {
              BlockingRpcChannel channel = rpcClient.createBlockingRpcChannel(sn,
                  User.getCurrent(), rpcTimeout);
              stub = makeStub(channel);
              isMasterRunning();
              stubs.put(key, stub);
            }
          }
          return stub;
        } finally {
          zkw.close();
        }
      }

      /**
       * Create a stub against the master.  Retry if necessary.
       * @return A stub to do <code>intf</code> against the master
       * @throws MasterNotRunningException
       */
      @edu.umd.cs.findbugs.annotations.SuppressWarnings (value="SWL_SLEEP_WITH_LOCK_HELD")
      Object makeStub() throws MasterNotRunningException {
        // The lock must be at the beginning to prevent multiple master creations
        //  (and leaks) in a multithread context
        synchronized (masterAndZKLock) {
          Exception exceptionCaught = null;
          Object stub = null;
          int tries = 0;
          while (!closed && stub == null) {
            tries++;
            try {
              stub = makeStubNoRetries();
            } catch (IOException e) {
              exceptionCaught = e;
            } catch (KeeperException e) {
              exceptionCaught = e;
            } catch (ServiceException e) {
              exceptionCaught = e;
            }

            if (exceptionCaught != null)
              // It failed. If it's not the last try, we're going to wait a little
              if (tries < numTries) {
                // tries at this point is 1 or more; decrement to start from 0.
                long pauseTime = ConnectionUtils.getPauseTime(pause, tries - 1);
                LOG.info("getMaster attempt " + tries + " of " + numTries +
                    " failed; retrying after sleep of " + pauseTime + ", exception=" +
                  exceptionCaught);

                try {
                  Thread.sleep(pauseTime);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new RuntimeException(
                      "Thread was interrupted while trying to connect to master.", e);
                }
              } else {
                // Enough tries, we stop now
                LOG.info("getMaster attempt " + tries + " of " + numTries +
                    " failed; no more retrying.", exceptionCaught);
                throw new MasterNotRunningException(exceptionCaught);
              }
          }

          if (stub == null) {
            // implies this.closed true
            throw new MasterNotRunningException("Connection was closed while trying to get master");
          }
          return stub;
        }
      }
    }

    /**
     * Class to make a MasterMonitorService stub.
     */
    class MasterMonitorServiceStubMaker extends StubMaker {
      private MasterMonitorService.BlockingInterface stub;
      @Override
      protected String getServiceName() {
        return MasterMonitorService.getDescriptor().getName();
      }

      @Override
      @edu.umd.cs.findbugs.annotations.SuppressWarnings("SWL_SLEEP_WITH_LOCK_HELD")
      MasterMonitorService.BlockingInterface makeStub() throws MasterNotRunningException {
        return (MasterMonitorService.BlockingInterface)super.makeStub();
      }

      @Override
      protected Object makeStub(BlockingRpcChannel channel) {
        this.stub = MasterMonitorService.newBlockingStub(channel);
        return this.stub;
      }

      @Override
      protected void isMasterRunning() throws ServiceException {
        this.stub.isMasterRunning(null, RequestConverter.buildIsMasterRunningRequest());
      }
    }

    /**
     * Class to make a MasterAdminService stub.
     */
    class MasterAdminServiceStubMaker extends StubMaker {
      private MasterAdminService.BlockingInterface stub;

      @Override
      protected String getServiceName() {
        return MasterAdminService.getDescriptor().getName();
      }

      @Override
      @edu.umd.cs.findbugs.annotations.SuppressWarnings("SWL_SLEEP_WITH_LOCK_HELD")
      MasterAdminService.BlockingInterface makeStub() throws MasterNotRunningException {
        return (MasterAdminService.BlockingInterface)super.makeStub();
      }

      @Override
      protected Object makeStub(BlockingRpcChannel channel) {
        this.stub = MasterAdminService.newBlockingStub(channel);
        return this.stub;
      }

      @Override
      protected void isMasterRunning() throws ServiceException {
        this.stub.isMasterRunning(null, RequestConverter.buildIsMasterRunningRequest());
      }
    };

    @Override
    public AdminService.BlockingInterface getAdmin(final ServerName serverName)
        throws IOException {
      return getAdmin(serverName, false);
    }

    @Override
    // Nothing is done w/ the 'master' parameter.  It is ignored.
    public AdminService.BlockingInterface getAdmin(final ServerName serverName,
      final boolean master)
    throws IOException {
      if (isDeadServer(serverName)) {
        throw new RegionServerStoppedException(serverName + " is dead.");
      }
      String key = getStubKey(AdminService.BlockingInterface.class.getName(),
        serverName.getHostAndPort());
      this.connectionLock.putIfAbsent(key, key);
      AdminService.BlockingInterface stub = null;
      synchronized (this.connectionLock.get(key)) {
        stub = (AdminService.BlockingInterface)this.stubs.get(key);
        if (stub == null) {
          BlockingRpcChannel channel = this.rpcClient.createBlockingRpcChannel(serverName,
            User.getCurrent(), this.rpcTimeout);
          stub = AdminService.newBlockingStub(channel);
          this.stubs.put(key, stub);
        }
      }
      return stub;
    }

    @Override
    public ClientService.BlockingInterface getClient(final ServerName sn)
    throws IOException {
      if (isDeadServer(sn)) {
        throw new RegionServerStoppedException(sn + " is dead.");
      }
      String key = getStubKey(ClientService.BlockingInterface.class.getName(), sn.getHostAndPort());
      this.connectionLock.putIfAbsent(key, key);
      ClientService.BlockingInterface stub = null;
      synchronized (this.connectionLock.get(key)) {
        stub = (ClientService.BlockingInterface)this.stubs.get(key);
        if (stub == null) {
          BlockingRpcChannel channel = this.rpcClient.createBlockingRpcChannel(sn,
            User.getCurrent(), this.rpcTimeout);
          stub = ClientService.newBlockingStub(channel);
          // In old days, after getting stub/proxy, we'd make a call.  We are not doing that here.
          // Just fail on first actual call rather than in here on setup.
          this.stubs.put(key, stub);
        }
      }
      return stub;
    }

    static String getStubKey(final String serviceName, final String rsHostnamePort) {
      return serviceName + "@" + rsHostnamePort;
    }

    private ZooKeeperKeepAliveConnection keepAliveZookeeper;
    private int keepAliveZookeeperUserCount;
    private boolean canCloseZKW = true;

    // keepAlive time, in ms. No reason to make it configurable.
    private static final long keepAlive = 5 * 60 * 1000;

    /**
     * Retrieve a shared ZooKeeperWatcher. You must close it it once you've have finished with it.
     * @return The shared instance. Never returns null.
     */
    ZooKeeperKeepAliveConnection getKeepAliveZooKeeperWatcher()
      throws IOException {
      synchronized (masterAndZKLock) {
        if (keepAliveZookeeper == null) {
          if (this.closed) {
            throw new IOException(toString() + " closed");
          }
          // We don't check that our link to ZooKeeper is still valid
          // But there is a retry mechanism in the ZooKeeperWatcher itself
          keepAliveZookeeper = new ZooKeeperKeepAliveConnection(conf, this.toString(), this);
        }
        keepAliveZookeeperUserCount++;
        keepZooKeeperWatcherAliveUntil = Long.MAX_VALUE;
        return keepAliveZookeeper;
      }
    }

    void releaseZooKeeperWatcher(final ZooKeeperWatcher zkw) {
      if (zkw == null){
        return;
      }
      synchronized (masterAndZKLock) {
        --keepAliveZookeeperUserCount;
        if (keepAliveZookeeperUserCount <= 0 ){
          keepZooKeeperWatcherAliveUntil = System.currentTimeMillis() + keepAlive;
        }
      }
    }

    /**
     * Creates a Chore thread to check the connections to master & zookeeper
     *  and close them when they reach their closing time (
     *  {@link MasterServiceState#keepAliveUntil} and
     *  {@link #keepZooKeeperWatcherAliveUntil}). Keep alive time is
     *  managed by the release functions and the variable {@link #keepAlive}
     */
    private static class DelayedClosing extends Chore implements Stoppable {
      private HConnectionImplementation hci;
      Stoppable stoppable;

      private DelayedClosing(
        HConnectionImplementation hci, Stoppable stoppable){
        super(
          "ZooKeeperWatcher and Master delayed closing for connection "+hci,
          60*1000, // We check every minutes
          stoppable);
        this.hci = hci;
        this.stoppable = stoppable;
      }

      static DelayedClosing createAndStart(HConnectionImplementation hci){
        Stoppable stoppable = new Stoppable() {
              private volatile boolean isStopped = false;
              @Override public void stop(String why) { isStopped = true;}
              @Override public boolean isStopped() {return isStopped;}
            };

        return new DelayedClosing(hci, stoppable);
      }

      protected void closeMasterProtocol(MasterServiceState protocolState) {
        if (System.currentTimeMillis() > protocolState.keepAliveUntil) {
          hci.closeMasterService(protocolState);
          protocolState.keepAliveUntil = Long.MAX_VALUE;
        }
      }

      @Override
      protected void chore() {
        synchronized (hci.masterAndZKLock) {
          if (hci.canCloseZKW) {
            if (System.currentTimeMillis() >
              hci.keepZooKeeperWatcherAliveUntil) {

              hci.closeZooKeeperWatcher();
              hci.keepZooKeeperWatcherAliveUntil = Long.MAX_VALUE;
            }
          }
          closeMasterProtocol(hci.adminMasterServiceState);
          closeMasterProtocol(hci.monitorMasterServiceState);
        }
      }

      @Override
      public void stop(String why) {
        stoppable.stop(why);
      }

      @Override
      public boolean isStopped() {
        return stoppable.isStopped();
      }
    }

    private void closeZooKeeperWatcher() {
      synchronized (masterAndZKLock) {
        if (keepAliveZookeeper != null) {
          LOG.info("Closing zookeeper sessionid=0x" +
            Long.toHexString(
              keepAliveZookeeper.getRecoverableZooKeeper().getSessionId()));
          keepAliveZookeeper.internalClose();
          keepAliveZookeeper = null;
        }
        keepAliveZookeeperUserCount = 0;
      }
    }

    final MasterAdminServiceState adminMasterServiceState = new MasterAdminServiceState(this);
    final MasterMonitorServiceState monitorMasterServiceState =
      new MasterMonitorServiceState(this);

    @Override
    public MasterAdminService.BlockingInterface getMasterAdmin() throws MasterNotRunningException {
      return getKeepAliveMasterAdminService();
    }

    @Override
    public MasterMonitorService.BlockingInterface getMasterMonitor()
    throws MasterNotRunningException {
      return getKeepAliveMasterMonitorService();
    }

    private void resetMasterServiceState(final MasterServiceState mss) {
      mss.userCount++;
      mss.keepAliveUntil = Long.MAX_VALUE;
    }

    @Override
    public MasterAdminKeepAliveConnection getKeepAliveMasterAdminService()
    throws MasterNotRunningException {
      synchronized (masterAndZKLock) {
        if (!isKeepAliveMasterConnectedAndRunning(this.adminMasterServiceState)) {
          MasterAdminServiceStubMaker stubMaker = new MasterAdminServiceStubMaker();
          this.adminMasterServiceState.stub = stubMaker.makeStub();
        }
        resetMasterServiceState(this.adminMasterServiceState);
      }
      // Ugly delegation just so we can add in a Close method.
      final MasterAdminService.BlockingInterface stub = this.adminMasterServiceState.stub;
      return new MasterAdminKeepAliveConnection() {
        MasterAdminServiceState mss = adminMasterServiceState;
        @Override
        public AddColumnResponse addColumn(RpcController controller,
            AddColumnRequest request) throws ServiceException {
          return stub.addColumn(controller, request);
        }

        @Override
        public DeleteColumnResponse deleteColumn(RpcController controller,
            DeleteColumnRequest request) throws ServiceException {
          return stub.deleteColumn(controller, request);
        }

        @Override
        public ModifyColumnResponse modifyColumn(RpcController controller,
            ModifyColumnRequest request) throws ServiceException {
          return stub.modifyColumn(controller, request);
        }

        @Override
        public MoveRegionResponse moveRegion(RpcController controller,
            MoveRegionRequest request) throws ServiceException {
          return stub.moveRegion(controller, request);
        }

        @Override
        public DispatchMergingRegionsResponse dispatchMergingRegions(
            RpcController controller, DispatchMergingRegionsRequest request)
            throws ServiceException {
          return stub.dispatchMergingRegions(controller, request);
        }

        @Override
        public AssignRegionResponse assignRegion(RpcController controller,
            AssignRegionRequest request) throws ServiceException {
          return stub.assignRegion(controller, request);
        }

        @Override
        public UnassignRegionResponse unassignRegion(RpcController controller,
            UnassignRegionRequest request) throws ServiceException {
          return stub.unassignRegion(controller, request);
        }

        @Override
        public OfflineRegionResponse offlineRegion(RpcController controller,
            OfflineRegionRequest request) throws ServiceException {
          return stub.offlineRegion(controller, request);
        }

        @Override
        public DeleteTableResponse deleteTable(RpcController controller,
            DeleteTableRequest request) throws ServiceException {
          return stub.deleteTable(controller, request);
        }

        @Override
        public EnableTableResponse enableTable(RpcController controller,
            EnableTableRequest request) throws ServiceException {
          return stub.enableTable(controller, request);
        }

        @Override
        public DisableTableResponse disableTable(RpcController controller,
            DisableTableRequest request) throws ServiceException {
          return stub.disableTable(controller, request);
        }

        @Override
        public ModifyTableResponse modifyTable(RpcController controller,
            ModifyTableRequest request) throws ServiceException {
          return stub.modifyTable(controller, request);
        }

        @Override
        public CreateTableResponse createTable(RpcController controller,
            CreateTableRequest request) throws ServiceException {
          return stub.createTable(controller, request);
        }

        @Override
        public ShutdownResponse shutdown(RpcController controller,
            ShutdownRequest request) throws ServiceException {
          return stub.shutdown(controller, request);
        }

        @Override
        public StopMasterResponse stopMaster(RpcController controller,
            StopMasterRequest request) throws ServiceException {
          return stub.stopMaster(controller, request);
        }

        @Override
        public BalanceResponse balance(RpcController controller,
            BalanceRequest request) throws ServiceException {
          return stub.balance(controller, request);
        }

        @Override
        public SetBalancerRunningResponse setBalancerRunning(
            RpcController controller, SetBalancerRunningRequest request)
            throws ServiceException {
          return stub.setBalancerRunning(controller, request);
        }

        @Override
        public CatalogScanResponse runCatalogScan(RpcController controller,
            CatalogScanRequest request) throws ServiceException {
          return stub.runCatalogScan(controller, request);
        }

        @Override
        public EnableCatalogJanitorResponse enableCatalogJanitor(
            RpcController controller, EnableCatalogJanitorRequest request)
            throws ServiceException {
          return stub.enableCatalogJanitor(controller, request);
        }

        @Override
        public IsCatalogJanitorEnabledResponse isCatalogJanitorEnabled(
            RpcController controller, IsCatalogJanitorEnabledRequest request)
            throws ServiceException {
          return stub.isCatalogJanitorEnabled(controller, request);
        }

        @Override
        public CoprocessorServiceResponse execMasterService(
            RpcController controller, CoprocessorServiceRequest request)
            throws ServiceException {
          return stub.execMasterService(controller, request);
        }

        @Override
        public TakeSnapshotResponse snapshot(RpcController controller,
            TakeSnapshotRequest request) throws ServiceException {
          return stub.snapshot(controller, request);
        }

        @Override
        public ListSnapshotResponse getCompletedSnapshots(
            RpcController controller, ListSnapshotRequest request)
            throws ServiceException {
          return stub.getCompletedSnapshots(controller, request);
        }

        @Override
        public DeleteSnapshotResponse deleteSnapshot(RpcController controller,
            DeleteSnapshotRequest request) throws ServiceException {
          return stub.deleteSnapshot(controller, request);
        }

        @Override
        public IsSnapshotDoneResponse isSnapshotDone(RpcController controller,
            IsSnapshotDoneRequest request) throws ServiceException {
          return stub.isSnapshotDone(controller, request);
        }

        @Override
        public RestoreSnapshotResponse restoreSnapshot(
            RpcController controller, RestoreSnapshotRequest request)
            throws ServiceException {
          return stub.restoreSnapshot(controller, request);
        }

        @Override
        public IsRestoreSnapshotDoneResponse isRestoreSnapshotDone(
            RpcController controller, IsRestoreSnapshotDoneRequest request)
            throws ServiceException {
          return stub.isRestoreSnapshotDone(controller, request);
        }

        @Override
        public IsMasterRunningResponse isMasterRunning(
            RpcController controller, IsMasterRunningRequest request)
            throws ServiceException {
          return stub.isMasterRunning(controller, request);
        }

        @Override
        public void close() {
          release(this.mss);
        }
      };
    }

    private static void release(MasterServiceState mss) {
      if (mss != null && mss.connection != null) {
        ((HConnectionImplementation)mss.connection).releaseMaster(mss);
      }
    }

    @Override
    public MasterMonitorKeepAliveConnection getKeepAliveMasterMonitorService()
    throws MasterNotRunningException {
      synchronized (masterAndZKLock) {
        if (!isKeepAliveMasterConnectedAndRunning(this.monitorMasterServiceState)) {
          MasterMonitorServiceStubMaker stubMaker = new MasterMonitorServiceStubMaker();
          this.monitorMasterServiceState.stub = stubMaker.makeStub();
        }
        resetMasterServiceState(this.monitorMasterServiceState);
      }
      // Ugly delegation just so can implement close
      final MasterMonitorService.BlockingInterface stub = this.monitorMasterServiceState.stub;
      return new MasterMonitorKeepAliveConnection() {
        final MasterMonitorServiceState mss = monitorMasterServiceState;
        @Override
        public GetSchemaAlterStatusResponse getSchemaAlterStatus(
            RpcController controller, GetSchemaAlterStatusRequest request)
            throws ServiceException {
          return stub.getSchemaAlterStatus(controller, request);
        }

        @Override
        public GetTableDescriptorsResponse getTableDescriptors(
            RpcController controller, GetTableDescriptorsRequest request)
            throws ServiceException {
          return stub.getTableDescriptors(controller, request);
        }

        @Override
        public GetClusterStatusResponse getClusterStatus(
            RpcController controller, GetClusterStatusRequest request)
            throws ServiceException {
          return stub.getClusterStatus(controller, request);
        }

        @Override
        public IsMasterRunningResponse isMasterRunning(
            RpcController controller, IsMasterRunningRequest request)
            throws ServiceException {
          return stub.isMasterRunning(controller, request);
        }

        @Override
        public void close() throws IOException {
          release(this.mss);
        }
      };
    }

    private boolean isKeepAliveMasterConnectedAndRunning(MasterServiceState mss) {
      if (mss.getStub() == null){
        return false;
      }
      try {
        return mss.isMasterRunning();
      } catch (UndeclaredThrowableException e) {
        // It's somehow messy, but we can receive exceptions such as
        //  java.net.ConnectException but they're not declared. So we catch it...
        LOG.info("Master connection is not running anymore", e.getUndeclaredThrowable());
        return false;
      } catch (ServiceException se) {
        LOG.warn("Checking master connection", se);
        return false;
      }
    }

    void releaseMaster(MasterServiceState mss) {
      if (mss.getStub() == null) return;
      synchronized (masterAndZKLock) {
        --mss.userCount;
        if (mss.userCount <= 0) {
          mss.keepAliveUntil = System.currentTimeMillis() + keepAlive;
        }
      }
    }

    private void closeMasterService(MasterServiceState mss) {
      if (mss.getStub() != null) {
        LOG.info("Closing master protocol: " + mss);
        mss.clearStub();
      }
      mss.userCount = 0;
    }

    /**
     * Immediate close of the shared master. Can be by the delayed close or when closing the
     * connection itself.
     */
    private void closeMaster() {
      synchronized (masterAndZKLock) {
        closeMasterService(adminMasterServiceState);
        closeMasterService(monitorMasterServiceState);
      }
    }

    @Override
    public <T> T getRegionServerWithRetries(ServerCallable<T> callable)
    throws IOException, RuntimeException {
      return callable.withRetries();
    }

    @Override
    public <T> T getRegionServerWithoutRetries(ServerCallable<T> callable)
    throws IOException, RuntimeException {
      return callable.withoutRetries();
    }

    @Deprecated
    private <R> Callable<MultiResponse> createCallable(final HRegionLocation loc,
        final MultiAction<R> multi, final byte[] tableName) {
      // TODO: This does not belong in here!!! St.Ack HConnections should
      // not be dealing in Callables; Callables have HConnections, not other
      // way around.
      final HConnection connection = this;
      return new Callable<MultiResponse>() {
        @Override
        public MultiResponse call() throws Exception {
          ServerCallable<MultiResponse> callable =
            new MultiServerCallable<R>(connection, tableName, loc, multi);
          return callable.withoutRetries();
        }
      };
   }

   void updateCachedLocation(HRegionInfo hri, HRegionLocation source,
       ServerName serverName, long seqNum) {
      HRegionLocation newHrl = new HRegionLocation(hri, serverName, seqNum);
      synchronized (this.cachedRegionLocations) {
        cacheLocation(hri.getTableName(), source, newHrl);
      }
    }

   /**
    * Deletes the cached location of the region if necessary, based on some error from source.
    * @param hri The region in question.
    * @param source The source of the error that prompts us to invalidate cache.
    */
    void deleteCachedLocation(HRegionInfo hri, HRegionLocation source) {
      boolean isStaleDelete = false;
      HRegionLocation oldLocation;
      synchronized (this.cachedRegionLocations) {
        Map<byte[], HRegionLocation> tableLocations =
          getTableLocations(hri.getTableName());
        oldLocation = tableLocations.get(hri.getStartKey());
        if (oldLocation != null) {
           // Do not delete the cache entry if it's not for the same server that gave us the error.
          isStaleDelete = (source != null) && !oldLocation.equals(source);
          if (!isStaleDelete) {
            tableLocations.remove(hri.getStartKey());
          }
        }
      }
    }

    @Override
    public void deleteCachedRegionLocation(final HRegionLocation location) {
      if (location == null) {
        return;
      }
      synchronized (this.cachedRegionLocations) {
        byte[] tableName = location.getRegionInfo().getTableName();
        Map<byte[], HRegionLocation> tableLocations = getTableLocations(tableName);
        if (!tableLocations.isEmpty()) {
          // Delete if there's something in the cache for this region.
          HRegionLocation removedLocation =
          tableLocations.remove(location.getRegionInfo().getStartKey());
          if (LOG.isDebugEnabled() && removedLocation != null) {
            LOG.debug("Removed " +
                location.getRegionInfo().getRegionNameAsString() +
                " for tableName=" + Bytes.toString(tableName) +
                " from cache");
          }
        }
      }
    }

    /**
     * Update the location with the new value (if the exception is a RegionMovedException)
     * or delete it from the cache.
     * @param exception an object (to simplify user code) on which we will try to find a nested
     *                  or wrapped or both RegionMovedException
     * @param source server that is the source of the location update.
     */
    private void updateCachedLocations(final byte[] tableName, Row row,
      final Object exception, final HRegionLocation source) {
      if (row == null || tableName == null) {
        LOG.warn("Coding error, see method javadoc. row=" + (row == null ? "null" : row) +
            ", tableName=" + (tableName == null ? "null" : Bytes.toString(tableName)));
        return;
      }

      // Is it something we have already updated?
      final HRegionLocation oldLocation = getCachedLocation(tableName, row.getRow());
      if (oldLocation == null) {
        // There is no such location in the cache => it's been removed already => nothing to do
        return;
      }

      HRegionInfo regionInfo = oldLocation.getRegionInfo();
      final RegionMovedException rme = RegionMovedException.find(exception);
      if (rme != null) {
        if (LOG.isTraceEnabled()){
          LOG.trace("Region " + regionInfo.getRegionNameAsString() + " moved to " +
            rme.getHostname() + ":" + rme.getPort() + " according to " + source.getHostnamePort());
        }
        updateCachedLocation(
            regionInfo, source, rme.getServerName(), rme.getLocationSeqNum());
      } else if (RegionOpeningException.find(exception) != null) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Region " + regionInfo.getRegionNameAsString() + " is being opened on "
              + source.getHostnamePort() + "; not deleting the cache entry");
        }
      } else {
        deleteCachedLocation(regionInfo, source);
      }
    }

    @Override
    @Deprecated
    public void processBatch(List<? extends Row> list,
        final byte[] tableName,
        ExecutorService pool,
        Object[] results) throws IOException, InterruptedException {
      // This belongs in HTable!!! Not in here.  St.Ack

      // results must be the same size as list
      if (results.length != list.size()) {
        throw new IllegalArgumentException(
          "argument results must be the same size as argument list");
      }
      processBatchCallback(list, tableName, pool, results, null);
    }

    /**
     * Send the queries in parallel on the different region servers. Retries on failures.
     * If the method returns it means that there is no error, and the 'results' array will
     * contain no exception. On error, an exception is thrown, and the 'results' array will
     * contain results and exceptions.
     * @deprecated since 0.96 - Use {@link HTable#processBatchCallback} instead
     */
    @Override
    @Deprecated
    public <R> void processBatchCallback(
      List<? extends Row> list,
      byte[] tableName,
      ExecutorService pool,
      Object[] results,
      Batch.Callback<R> callback)
      throws IOException, InterruptedException {

      Process<R> p = new Process<R>(this, list, tableName, pool, results, callback);
      p.processBatchCallback();
    }


    /**
     * Methods and attributes to manage a batch process are grouped into this single class.
     * This allows, by creating a Process<R> per batch process to ensure multithread safety.
     *
     * This code should be move to HTable once processBatchCallback is not supported anymore in
     * the HConnection interface.
     */
    private static class Process<R> {
      // Info on the queries and their context
      private final HConnectionImplementation hci;
      private final List<? extends Row> rows;
      private final byte[] tableName;
      private final ExecutorService pool;
      private final Object[] results;
      private final Batch.Callback<R> callback;

      // Used during the batch process
      private final List<Action<R>> toReplay;
      private final LinkedList<Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>>>
        inProgress;

      private ServerErrorTracker errorsByServer = null;
      private int curNumRetries;

      // Notified when a tasks is done
      private final List<MultiAction<R>> finishedTasks = new ArrayList<MultiAction<R>>();

      private Process(HConnectionImplementation hci, List<? extends Row> list,
                       byte[] tableName, ExecutorService pool, Object[] results,
                       Batch.Callback<R> callback){
        this.hci = hci;
        this.rows = list;
        this.tableName = tableName;
        this.pool = pool;
        this.results = results;
        this.callback = callback;
        this.toReplay = new ArrayList<Action<R>>();
        this.inProgress =
          new LinkedList<Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>>>();
        this.curNumRetries = 0;
      }


      /**
       * Group a list of actions per region servers, and send them. The created MultiActions are
       *  added to the inProgress list.
       * @param actionsList
       * @param isRetry Whether we are retrying these actions. If yes, backoff
       *                time may be applied before new requests.
       * @throws IOException - if we can't locate a region after multiple retries.
       */
      private void submit(List<Action<R>> actionsList, final boolean isRetry) throws IOException {
        // group per location => regions server
        final Map<HRegionLocation, MultiAction<R>> actionsByServer =
          new HashMap<HRegionLocation, MultiAction<R>>();
        for (Action<R> aAction : actionsList) {
          final Row row = aAction.getAction();

          if (row != null) {
            final HRegionLocation loc = hci.locateRegion(this.tableName, row.getRow());
            if (loc == null) {
              throw new IOException("No location found, aborting submit.");
            }

            final byte[] regionName = loc.getRegionInfo().getRegionName();
            MultiAction<R> actions = actionsByServer.get(loc);
            if (actions == null) {
              actions = new MultiAction<R>();
              actionsByServer.put(loc, actions);
            }
            actions.add(regionName, aAction);
          }
        }

        // Send the queries and add them to the inProgress list
        for (Entry<HRegionLocation, MultiAction<R>> e : actionsByServer.entrySet()) {
          long backoffTime = 0;
          if (isRetry) {
            if (hci.isUsingServerTrackerForRetries()) {
              assert this.errorsByServer != null;
              backoffTime = this.errorsByServer.calculateBackoffTime(e.getKey(), hci.pause);
            } else {
              // curNumRetries starts with one, subtract to start from 0.
              backoffTime = ConnectionUtils.getPauseTime(hci.pause, curNumRetries - 1);
            }
          }
          Callable<MultiResponse> callable =
            createDelayedCallable(backoffTime, e.getKey(), e.getValue());
          if (LOG.isTraceEnabled() && isRetry) {
            StringBuilder sb = new StringBuilder();
            for (Action<R> action : e.getValue().allActions()) {
              if (sb.length() > 0) sb.append(' ');
              sb.append(Bytes.toStringBinary(action.getAction().getRow()));
            }
            LOG.trace("Attempt #" + this.curNumRetries + " against " + e.getKey().getHostnamePort()
              + " after=" + backoffTime + "ms, row(s)=" + sb.toString());
          }
          Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>> p =
            new Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>>(
              e.getValue(), e.getKey(), this.pool.submit(callable));
          this.inProgress.addLast(p);
        }
      }

     /**
      * Resubmit the actions which have failed, after a sleep time.
      * @throws IOException
      */
      private void doRetry() throws IOException{
        submit(this.toReplay, true);
        this.toReplay.clear();
      }

      /**
       * Parameterized batch processing, allowing varying return types for
       * different {@link Row} implementations.
       * Throws an exception on error. If there are no exceptions, it means that the 'results'
       *  array is clean.
       */
      private void processBatchCallback() throws IOException, InterruptedException {
        if (this.results.length != this.rows.size()) {
          throw new IllegalArgumentException(
            "argument results (size="+results.length+") must be the same size as " +
              "argument list (size="+this.rows.size()+")");
        }
        if (this.rows.isEmpty()) {
          return;
        }

        boolean isTraceEnabled = LOG.isTraceEnabled();
        BatchErrors errors = new BatchErrors();
        BatchErrors retriedErrors = null;
        if (isTraceEnabled) {
          retriedErrors = new BatchErrors();
        }

        // We keep the number of retry per action.
        int[] nbRetries = new int[this.results.length];

        // Build the action list. This list won't change after being created, hence the
        //  indexes will remain constant, allowing a direct lookup.
        final List<Action<R>> listActions = new ArrayList<Action<R>>(this.rows.size());
        for (int i = 0; i < this.rows.size(); i++) {
          Action<R> action = new Action<R>(this.rows.get(i), i);
          listActions.add(action);
        }

        // execute the actions. We will analyze and resubmit the actions in a 'while' loop.
        submit(listActions, false);

        // LastRetry is true if, either:
        //  we had an exception 'DoNotRetry'
        //  we had more than numRetries for any action
        //  In this case, we will finish the current retries but we won't start new ones.
        boolean lastRetry = false;
        // If hci.numTries is 1 or 0, we do not retry.
        boolean noRetry = (hci.numTries < 2);

        // Analyze and resubmit until all actions are done successfully or failed after numTries
        while (!this.inProgress.isEmpty()) {
          // We need the original multi action to find out what actions to replay if
          //  we have a 'total' failure of the Future<MultiResponse>
          // We need the HRegionLocation as we give it back if we go out of retries
          Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>> currentTask =
            removeFirstDone();

          // Get the answer, keep the exception if any as we will use it for the analysis
          MultiResponse responses = null;
          ExecutionException exception = null;
          try {
            responses = currentTask.getThird().get();
          } catch (ExecutionException e) {
            exception = e;
          }
          HRegionLocation location = currentTask.getSecond();
          // Error case: no result at all for this multi action. We need to redo all actions
          if (responses == null) {
            for (List<Action<R>> actions : currentTask.getFirst().actions.values()) {
              for (Action<R> action : actions) {
                Row row = action.getAction();
                // Do not use the exception for updating cache because it might be coming from
                // any of the regions in the MultiAction.
                hci.updateCachedLocations(tableName, row, null, location);
                if (noRetry) {
                  errors.add(exception, row, location);
                } else {
                  if (isTraceEnabled) {
                    retriedErrors.add(exception, row, location);
                  }
                  lastRetry = addToReplay(nbRetries, action, location);
                }
              }
            }
          } else { // Success or partial success
            // Analyze detailed results. We can still have individual failures to be redo.
            // two specific exceptions are managed:
            //  - DoNotRetryIOException: we continue to retry for other actions
            //  - RegionMovedException: we update the cache with the new region location
            for (Entry<byte[], List<Pair<Integer, Object>>> resultsForRS :
                responses.getResults().entrySet()) {
              for (Pair<Integer, Object> regionResult : resultsForRS.getValue()) {
                Action<R> correspondingAction = listActions.get(regionResult.getFirst());
                Object result = regionResult.getSecond();
                this.results[correspondingAction.getOriginalIndex()] = result;

                // Failure: retry if it's make sense else update the errors lists
                if (result == null || result instanceof Throwable) {
                  Row row = correspondingAction.getAction();
                  hci.updateCachedLocations(this.tableName, row, result, location);
                  if (result instanceof DoNotRetryIOException || noRetry) {
                    errors.add((Exception)result, row, location);
                  } else {
                    if (isTraceEnabled) {
                      retriedErrors.add((Exception)result, row, location);
                    }
                    lastRetry = addToReplay(nbRetries, correspondingAction, location);
                  }
                } else // success
                  if (callback != null) {
                    this.callback.update(resultsForRS.getKey(),
                      this.rows.get(regionResult.getFirst()).getRow(), (R) result);
                }
              }
            }
          }

          // Retry all actions in toReplay then clear it.
          if (!noRetry && !toReplay.isEmpty()) {
            if (isTraceEnabled) {
              LOG.trace("Retrying #" + this.curNumRetries +
                (lastRetry ? " (one last time)": "") + " because " +
                retriedErrors.getDescriptionAndClear());
            }
            doRetry();
            if (lastRetry) {
              noRetry = true;
            }
          }
        }

        errors.rethrowIfAny();
      }


      private class BatchErrors {
        private List<Throwable> exceptions = new ArrayList<Throwable>();
        private List<Row> actions = new ArrayList<Row>();
        private List<String> addresses = new ArrayList<String>();

        public void add(Exception ex, Row row, HRegionLocation location) {
          exceptions.add(ex);
          actions.add(row);
          addresses.add(location.getHostnamePort());
        }

        public void rethrowIfAny() throws RetriesExhaustedWithDetailsException {
          if (!exceptions.isEmpty()) {
            throw makeException();
          }
        }

        public String getDescriptionAndClear(){
          if (exceptions.isEmpty()) {
            return "";
          }
          String result = makeException().getExhaustiveDescription();
          exceptions.clear();
          actions.clear();
          addresses.clear();
          return result;
        }

        private RetriesExhaustedWithDetailsException makeException() {
          return new RetriesExhaustedWithDetailsException(exceptions, actions, addresses);
        }
      }

      /**
       * Put the action that has to be retried in the Replay list.
       * @return true if we're out of numRetries and it's the last retry.
       */
      private boolean addToReplay(int[] nbRetries, Action<R> action, HRegionLocation source) {
        this.toReplay.add(action);
        nbRetries[action.getOriginalIndex()]++;
        if (nbRetries[action.getOriginalIndex()] > this.curNumRetries) {
          this.curNumRetries = nbRetries[action.getOriginalIndex()];
        }
        if (hci.isUsingServerTrackerForRetries()) {
          if (this.errorsByServer == null) {
            this.errorsByServer = hci.createServerErrorTracker();
          }
          this.errorsByServer.reportServerError(source);
          return !this.errorsByServer.canRetryMore();
        } else {
          // We need to add 1 to make tries and retries comparable. And as we look for
          // the last try we compare with '>=' and not '>'. And we need curNumRetries
          // to means what it says as we don't want to initialize it to 1.
          return ((this.curNumRetries + 1) >= hci.numTries);
        }
      }

      /**
       * Wait for one of tasks to be done, and remove it from the list.
       * @return the tasks done.
       */
      private Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>>
      removeFirstDone() throws InterruptedException {
        while (true) {
          synchronized (finishedTasks) {
            if (!finishedTasks.isEmpty()) {
              MultiAction<R> done = finishedTasks.remove(finishedTasks.size() - 1);

              // We now need to remove it from the inProgress part.
              Iterator<Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>>> it =
                inProgress.iterator();
              while (it.hasNext()) {
                Triple<MultiAction<R>, HRegionLocation, Future<MultiResponse>> task = it.next();
                if (task.getFirst() == done) { // We have the exact object. No java equals here.
                  it.remove();
                  return task;
                }
              }
              LOG.error("Development error: We didn't see a task in the list. " +
                done.getRegions());
            }
            finishedTasks.wait(10);
          }
        }
      }

      private Callable<MultiResponse> createDelayedCallable(
        final long delay, final HRegionLocation loc, final MultiAction<R> multi) {

        final Callable<MultiResponse> delegate = hci.createCallable(loc, multi, tableName);

        return new Callable<MultiResponse>() {
          private final long creationTime = System.currentTimeMillis();

          @Override
          public MultiResponse call() throws Exception {
            try {
              final long waitingTime = delay + creationTime - System.currentTimeMillis();
              if (waitingTime > 0) {
                Thread.sleep(waitingTime);
              }
              return delegate.call();
            } finally {
              synchronized (finishedTasks) {
                finishedTasks.add(multi);
                finishedTasks.notifyAll();
              }
            }
          }
        };
      }
    }

    /*
     * Return the number of cached region for a table. It will only be called
     * from a unit test.
     */
    int getNumberOfCachedRegionLocations(final byte[] tableName) {
      Integer key = Bytes.mapKey(tableName);
      synchronized (this.cachedRegionLocations) {
        Map<byte[], HRegionLocation> tableLocs = this.cachedRegionLocations.get(key);
        if (tableLocs == null) {
          return 0;
        }
        return tableLocs.values().size();
      }
    }

    /**
     * Check the region cache to see whether a region is cached yet or not.
     * Called by unit tests.
     * @param tableName tableName
     * @param row row
     * @return Region cached or not.
     */
    boolean isRegionCached(final byte[] tableName, final byte[] row) {
      HRegionLocation location = getCachedLocation(tableName, row);
      return location != null;
    }

    @Override
    public void setRegionCachePrefetch(final byte[] tableName,
        final boolean enable) {
      if (!enable) {
        regionCachePrefetchDisabledTables.add(Bytes.mapKey(tableName));
      }
      else {
        regionCachePrefetchDisabledTables.remove(Bytes.mapKey(tableName));
      }
    }

    @Override
    public boolean getRegionCachePrefetch(final byte[] tableName) {
      return !regionCachePrefetchDisabledTables.contains(Bytes.mapKey(tableName));
    }

    @Override
    public void abort(final String msg, Throwable t) {
      if (t instanceof KeeperException.SessionExpiredException
        && keepAliveZookeeper != null) {
        synchronized (masterAndZKLock) {
          if (keepAliveZookeeper != null) {
            LOG.warn("This client just lost it's session with ZooKeeper," +
              " closing it." +
              " It will be recreated next time someone needs it", t);
            closeZooKeeperWatcher();
          }
        }
      } else {
        if (t != null) {
          LOG.fatal(msg, t);
        } else {
          LOG.fatal(msg);
        }
        this.aborted = true;
        close();
        this.closed = true;
      }
    }

    @Override
    public boolean isClosed() {
      return this.closed;
    }

    @Override
    public boolean isAborted(){
      return this.aborted;
    }

    @Override
    public int getCurrentNrHRS() throws IOException {
      return this.registry.getCurrentNrHRS();
    }

    /**
     * Increment this client's reference count.
     */
    void incCount() {
      ++refCount;
    }

    /**
     * Decrement this client's reference count.
     */
    void decCount() {
      if (refCount > 0) {
        --refCount;
      }
    }

    /**
     * Return if this client has no reference
     *
     * @return true if this client has no reference; false otherwise
     */
    boolean isZeroReference() {
      return refCount == 0;
    }

    void internalClose() {
      if (this.closed) {
        return;
      }
      delayedClosing.stop("Closing connection");
      closeMaster();
      this.closed = true;
      closeZooKeeperWatcher();
      this.stubs.clear();
      if (clusterStatusListener != null) {
        clusterStatusListener.close();
      }
    }

    @Override
    public void close() {
      if (managed) {
        if (aborted) {
          HConnectionManager.deleteStaleConnection(this);
        } else {
          HConnectionManager.deleteConnection(this, false);
        }
      } else {
        internalClose();
      }
    }

    /**
     * Close the connection for good, regardless of what the current value of
     * {@link #refCount} is. Ideally, {@link #refCount} should be zero at this
     * point, which would be the case if all of its consumers close the
     * connection. However, on the off chance that someone is unable to close
     * the connection, perhaps because it bailed out prematurely, the method
     * below will ensure that this {@link HConnection} instance is cleaned up.
     * Caveat: The JVM may take an unknown amount of time to call finalize on an
     * unreachable object, so our hope is that every consumer cleans up after
     * itself, like any good citizen.
     */
    @Override
    protected void finalize() throws Throwable {
      super.finalize();
      // Pretend as if we are about to release the last remaining reference
      refCount = 1;
      close();
    }

    @Override
    public HTableDescriptor[] listTables() throws IOException {
      MasterMonitorKeepAliveConnection master = getKeepAliveMasterMonitorService();
      try {
        GetTableDescriptorsRequest req =
          RequestConverter.buildGetTableDescriptorsRequest(null);
        return ProtobufUtil.getHTableDescriptorArray(master.getTableDescriptors(null, req));
      } catch (ServiceException se) {
        throw ProtobufUtil.getRemoteException(se);
      } finally {
        master.close();
      }
    }

    @Override
    public HTableDescriptor[] getHTableDescriptors(List<String> tableNames) throws IOException {
      if (tableNames == null || tableNames.isEmpty()) return new HTableDescriptor[0];
      MasterMonitorKeepAliveConnection master = getKeepAliveMasterMonitorService();
      try {
        GetTableDescriptorsRequest req =
          RequestConverter.buildGetTableDescriptorsRequest(tableNames);
        return ProtobufUtil.getHTableDescriptorArray(master.getTableDescriptors(null, req));
      } catch (ServiceException se) {
        throw ProtobufUtil.getRemoteException(se);
      } finally {
        master.close();
      }
    }

    /**
     * Connects to the master to get the table descriptor.
     * @param tableName table name
     * @return
     * @throws IOException if the connection to master fails or if the table
     *  is not found.
     */
    @Override
    public HTableDescriptor getHTableDescriptor(final byte[] tableName)
    throws IOException {
      if (tableName == null || tableName.length == 0) return null;
      if (Bytes.equals(tableName, HConstants.META_TABLE_NAME)) {
        return HTableDescriptor.META_TABLEDESC;
      }
      MasterMonitorKeepAliveConnection master = getKeepAliveMasterMonitorService();
      GetTableDescriptorsResponse htds;
      try {
        GetTableDescriptorsRequest req =
          RequestConverter.buildGetTableDescriptorsRequest(null);
        htds = master.getTableDescriptors(null, req);
      } catch (ServiceException se) {
        throw ProtobufUtil.getRemoteException(se);
      } finally {
        master.close();
      }
      for (TableSchema ts : htds.getTableSchemaList()) {
        if (Bytes.equals(tableName, ts.getName().toByteArray())) {
          return HTableDescriptor.convert(ts);
        }
      }
      throw new TableNotFoundException(Bytes.toString(tableName));
    }

    /**
     * The record of errors for servers. Visible for testing.
     */
    @VisibleForTesting
    static class ServerErrorTracker {
      private final Map<HRegionLocation, ServerErrors> errorsByServer =
          new HashMap<HRegionLocation, ServerErrors>();
      private long canRetryUntil = 0;

      public ServerErrorTracker(long timeout) {
        LOG.trace("Server tracker timeout is " + timeout + "ms");
        this.canRetryUntil = EnvironmentEdgeManager.currentTimeMillis() + timeout;
      }

      boolean canRetryMore() {
        return EnvironmentEdgeManager.currentTimeMillis() < this.canRetryUntil;
      }

      /**
       * Calculates the back-off time for a retrying request to a particular server.
       * This is here, and package private, for testing (no good way to get at it).
       * @param server The server in question.
       * @param basePause The default hci pause.
       * @return The time to wait before sending next request.
       */
      long calculateBackoffTime(HRegionLocation server, long basePause) {
        long result = 0;
        ServerErrors errorStats = errorsByServer.get(server);
        if (errorStats != null) {
          result = ConnectionUtils.getPauseTime(basePause, errorStats.retries);
          // Adjust by the time we already waited since last talking to this server.
          long now = EnvironmentEdgeManager.currentTimeMillis();
          long timeSinceLastError = now - errorStats.getLastErrorTime();
          if (timeSinceLastError > 0) {
            result = Math.max(0, result - timeSinceLastError);
          }
          // Finally, see if the backoff time overshoots the timeout.
          if (result > 0 && (now + result > this.canRetryUntil)) {
            result = Math.max(0, this.canRetryUntil - now);
          }
        }
        return result;
      }

      /**
       * Reports that there was an error on the server to do whatever bean-counting necessary.
       * This is here, and package private, for testing (no good way to get at it).
       * @param server The server in question.
       */
      void reportServerError(HRegionLocation server) {
        ServerErrors errors = errorsByServer.get(server);
        if (errors != null) {
          errors.addError();
        } else {
          errorsByServer.put(server, new ServerErrors());
        }
      }

      /**
       * The record of errors for a server.
       */
      private static class ServerErrors {
        public long lastErrorTime;
        public int retries;

        public ServerErrors() {
          this.lastErrorTime = EnvironmentEdgeManager.currentTimeMillis();
          this.retries = 0;
        }

        public void addError() {
          this.lastErrorTime = EnvironmentEdgeManager.currentTimeMillis();
          ++this.retries;
        }

        public long getLastErrorTime() {
          return this.lastErrorTime;
        }
      }
    }

    public boolean isUsingServerTrackerForRetries() {
      return this.useServerTrackerForRetries;
    }
    /**
     * Creates the server error tracker to use inside process.
     * Currently, to preserve the main assumption about current retries, and to work well with
     * the retry-limit-based calculation, the calculation is local per Process object.
     * We may benefit from connection-wide tracking of server errors.
     * @return ServerErrorTracker to use.
     */
    ServerErrorTracker createServerErrorTracker() {
      return new ServerErrorTracker(this.serverTrackerTimeout);
    }
  }
  /**
   * Set the number of retries to use serverside when trying to communicate
   * with another server over {@link HConnection}.  Used updating catalog
   * tables, etc.  Call this method before we create any Connections.
   * @param c The Configuration instance to set the retries into.
   * @param log Used to log what we set in here.
   */
  public static void setServerSideHConnectionRetries(final Configuration c, final String sn,
      final Log log) {
    int hcRetries = c.getInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER,
      HConstants.DEFAULT_HBASE_CLIENT_RETRIES_NUMBER);
    // Go big.  Multiply by 10.  If we can't get to meta after this many retries
    // then something seriously wrong.
    int serversideMultiplier = c.getInt("hbase.client.serverside.retries.multiplier", 10);
    int retries = hcRetries * serversideMultiplier;
    c.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, retries);
    log.debug(sn + " HConnection server-to-server retries=" + retries);
  }
}
