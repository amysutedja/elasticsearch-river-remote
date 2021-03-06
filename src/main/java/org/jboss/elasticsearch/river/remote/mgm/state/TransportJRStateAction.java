/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.remote.mgm.state;

import java.util.Date;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.jboss.elasticsearch.river.remote.IRiverMgm;
import org.jboss.elasticsearch.river.remote.mgm.TransportJRMgmBaseAction;

/**
 * RemoteRiver state information transport action.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportJRStateAction extends
		TransportJRMgmBaseAction<JRStateRequest, JRStateResponse, NodeJRStateRequest, NodeJRStateResponse> {

	@Inject
	public TransportJRStateAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
			ClusterService clusterService, TransportService transportService) {
		super(settings, clusterName, threadPool, clusterService, transportService);
	}

	@Override
	protected String transportAction() {
		return JRStateAction.NAME;
	}

	@Override
	protected NodeJRStateResponse performOperationOnRiver(IRiverMgm river, JRStateRequest req, DiscoveryNode node)
			throws Exception {
		logger.debug("Go to get state information from river '{}'", req.getRiverName());
		String ret = river.getRiverOperationInfo(node, new Date());
		return new NodeJRStateResponse(node, true, ret);
	}

	@Override
	protected JRStateRequest newRequest() {
		return new JRStateRequest();
	}

	@Override
	protected NodeJRStateRequest newNodeRequest() {
		return new NodeJRStateRequest();
	}

	@Override
	protected NodeJRStateRequest newNodeRequest(String nodeId, JRStateRequest request) {
		return new NodeJRStateRequest(nodeId, request);
	}

	@Override
	protected NodeJRStateResponse newNodeResponse() {
		return new NodeJRStateResponse(clusterService.localNode());
	}

	@Override
	protected NodeJRStateResponse[] newNodeResponseArray(int len) {
		return new NodeJRStateResponse[len];
	}

	@Override
	protected JRStateResponse newResponse(ClusterName clusterName, NodeJRStateResponse[] array) {
		return new JRStateResponse(clusterName, array);
	}

}
