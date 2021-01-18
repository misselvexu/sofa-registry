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
package com.alipay.sofa.registry.server.session.resource;

import com.alipay.sofa.registry.common.model.Node.NodeType;
import com.alipay.sofa.registry.server.session.bootstrap.SessionServerConfig;
import com.alipay.sofa.registry.server.session.node.NodeManager;
import com.alipay.sofa.registry.server.session.node.NodeManagerFactory;
import com.alipay.sofa.registry.server.session.node.SessionNodeManager;
import com.google.common.base.Joiner;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author shangyu.wh
 * @version $Id: SessionOpenResource.java, v 0.1 2018-03-21 11:06 shangyu.wh Exp $
 */
@Path("api/servers")
public class SessionOpenResource {

    @Autowired
    private SessionServerConfig sessionServerConfig;

    @GET
    @Path("query.json")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getSessionServerListJson(@QueryParam("zone") String zone) {

        List<String> serverList = new ArrayList<>();
        NodeManager nodeManager = NodeManagerFactory.getNodeManager(NodeType.SESSION);

        if (StringUtils.isEmpty(zone)) {
            zone = sessionServerConfig.getSessionServerRegion();
        }

        if (StringUtils.isNotBlank(zone)) {
            zone = zone.toUpperCase();
        }

        if (nodeManager instanceof SessionNodeManager) {
            SessionNodeManager sessionNodeManager = (SessionNodeManager) nodeManager;
            serverList = sessionNodeManager.getZoneServerList(zone);

            serverList = serverList.stream()
                .map(server -> server + ":" + sessionServerConfig.getServerPort())
                .collect(Collectors.toList());
        }

        return serverList;
    }

    @GET
    @Path("query")
    @Produces(MediaType.TEXT_PLAIN)
    public String getSessionServerList(@QueryParam("zone") String zone) {
        return Joiner.on(";").join(getSessionServerListJson(zone));
    }

    @GET
    @Path("alive")
    public String checkAlive() {
        return "OK";
    }

}