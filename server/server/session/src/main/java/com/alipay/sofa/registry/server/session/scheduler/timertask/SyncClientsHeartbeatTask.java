/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.registry.server.session.scheduler.timertask;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadPoolExecutor;

import com.alipay.remoting.ProtocolCode;
import com.alipay.remoting.ProtocolManager;
import com.alipay.remoting.rpc.protocol.RpcProtocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.alipay.sofa.registry.log.Logger;
import com.alipay.sofa.registry.log.LoggerFactory;
import com.alipay.sofa.registry.metrics.TaskMetrics;
import com.alipay.sofa.registry.remoting.Server;
import com.alipay.sofa.registry.remoting.exchange.Exchange;
import com.alipay.sofa.registry.server.session.bootstrap.SessionServerConfig;
import com.alipay.sofa.registry.server.session.listener.ReceivedDataMultiPushTaskListener;
import com.alipay.sofa.registry.server.session.scheduler.ExecutorManager;
import com.alipay.sofa.registry.server.session.store.DataStore;
import com.alipay.sofa.registry.server.session.store.Interests;
import com.alipay.sofa.registry.server.session.store.Watchers;
import com.alipay.sofa.registry.task.batcher.AcceptorExecutor;
import com.alipay.sofa.registry.task.batcher.TaskDispatcher;
import com.alipay.sofa.registry.task.batcher.TaskDispatchers;
import com.alipay.sofa.registry.task.listener.TaskListener;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

/**
 * The type Sync clients heartbeat task.
 * @author zhuoyu.sjw
 * @version $Id : SyncClientsHeartbeatTask.java, v 0.1 2018-03-31 16:07 zhuoyu.sjw Exp $$
 */
public class SyncClientsHeartbeatTask {
    private static final Logger CONSOLE_COUNT_LOGGER = LoggerFactory.getLogger("SESSION-CONSOLE",
                                                         "[Count]");

    private static final Logger PRO_LOGGER           = LoggerFactory.getLogger(
                                                         "SESSION-PROFILE-DIGEST", "[TaskExecute]");

    private static final Logger EXE_LOGGER           = LoggerFactory.getLogger(
                                                         "SESSION-PROFILE-DIGEST",
                                                         "[ExecutorMetrics]");

    public static final String  SYMBOLIC             = "  └─ ";

    @Autowired
    private Exchange            boltExchange;

    @Autowired
    private SessionServerConfig sessionServerConfig;

    /**
     * store subscribers
     */
    @Autowired
    private Interests           sessionInterests;

    /**
     * store watchers
     */
    @Autowired
    private Watchers            sessionWatchers;

    /**
     * store publishers
     */
    @Autowired
    private DataStore           sessionDataStore;

    @Autowired
    private TaskListener        receivedDataMultiPushTaskListener;
    private final TaskMetrics   taskMetrics          = TaskMetrics.getInstance();

    public SyncClientsHeartbeatTask() {

        ThreadPoolExecutor boltDefaultExecutor = (ThreadPoolExecutor) ProtocolManager
            .getProtocol(ProtocolCode.fromBytes(RpcProtocol.PROTOCOL_CODE)).getCommandHandler()
            .getDefaultExecutor();
        taskMetrics.registerThreadExecutor("Session-BoltDefaultExecutor", boltDefaultExecutor);
    }

    @Scheduled(initialDelayString = "${session.server.syncHeartbeat.fixedDelay}", fixedDelayString = "${session.server.syncHeartbeat.fixedDelay}")
    public void syncCounte() {
        long countSub = sessionInterests.count();
        long countPub = sessionDataStore.count();
        long countSubW = sessionWatchers.count();

        int channelCount = 0;
        Server sessionServer = boltExchange.getServer(sessionServerConfig.getServerPort());
        if (sessionServer != null) {
            channelCount = sessionServer.getChannelCount();
        }

        CONSOLE_COUNT_LOGGER.info(
            "Subscriber count: {}, Publisher count: {}, Watcher count: {}, Connection count: {}",
            countSub, countPub, countSubW, channelCount);
    }

    @Scheduled(initialDelayString = "${session.server.printTask.fixedDelay}", fixedDelayString = "${session.server.printTask.fixedDelay}")
    public void printTaskExecute() {

        Map<String, TaskDispatcher> taskDispatcherMap = TaskDispatchers.getTaskDispatcherMap();
        if (taskDispatcherMap != null) {

            StringBuilder sb = new StringBuilder();
            logInfo(sb, taskDispatcherMap, "TaskDispatcher");
            PRO_LOGGER.info(sb.toString());
        }

    }

    protected void logInfo(StringBuilder sb0, Map<String, TaskDispatcher> taskDispatcherMap,
                           String info) {
        sb0.append("\n").append(info).append(" >>>>>>>");
        StringBuilder sb = new StringBuilder();
        for (Iterator<Entry<String, TaskDispatcher>> i = taskDispatcherMap.entrySet().iterator(); i
            .hasNext();) {
            Entry<String, TaskDispatcher> entry = i.next();
            AcceptorExecutor acceptorExecutor = entry.getValue().getAcceptorExecutor();
            sb.append(SYMBOLIC).append(entry.getKey());
            sb.append(", AcceptedTasks:").append(acceptorExecutor.getAcceptedTasks());
            sb.append(", ReplayedTasks:").append(acceptorExecutor.getReplayedTasks());
            sb.append(", QueueOverflows:").append(acceptorExecutor.getQueueOverflows());
            sb.append(" ,PendingTaskSize:").append(acceptorExecutor.getPendingTaskSize());
            sb.append(", ExpiredTasks:").append(acceptorExecutor.getExpiredTasks());
            sb.append(", OverriddenTasks:").append(acceptorExecutor.getOverriddenTasks());
            sb.append(", MaxBuffer:").append(acceptorExecutor.getMaxBufferSize()).append("\n");
        }
        sb0.append("\n").append(sb);
    }

    @Scheduled(initialDelayString = "${session.server.printTask.fixedDelay}", fixedDelayString = "${session.server.printTask.fixedDelay}")
    public void printExecutorTaskExecute() {
        EXE_LOGGER.info(TaskMetrics.getInstance().metricsString());
    }

    @Scheduled(initialDelayString = "${session.server.printTask.fixedDelay}", fixedDelayString = "${session.server.printTask.fixedDelay}")
    public void printPushMultiTaskExecute() {
        if (receivedDataMultiPushTaskListener instanceof ReceivedDataMultiPushTaskListener) {
            ReceivedDataMultiPushTaskListener listener = (ReceivedDataMultiPushTaskListener) receivedDataMultiPushTaskListener;
            StringBuilder sb = new StringBuilder();
            sb.append("ReceivedDataPush").append(" >>>>>>>");
            sb.append(", AcceptedTasks:").append(
                listener.getTaskMergeProcessorStrategy().getPutTaskSize());
            sb.append(", SendTasks:").append(
                listener.getTaskMergeProcessorStrategy().getSendTaskSize());
            sb.append(" ,PendingTaskSize:").append(
                listener.getTaskMergeProcessorStrategy().getPendingTaskSize());
            sb.append(", OverriddenTasks:").append(
                listener.getTaskMergeProcessorStrategy().getOverrideTaskSize());
            sb.append("\n");
            PRO_LOGGER.info(sb.toString());
        }
    }
}
