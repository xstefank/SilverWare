/*
 * -----------------------------------------------------------------------\
 * SilverWare
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package io.silverware.microservices.providers.cluster;

import io.silverware.microservices.Context;
import io.silverware.microservices.MicroserviceMetaData;
import io.silverware.microservices.providers.MicroserviceProvider;
import io.silverware.microservices.providers.cluster.internal.FutureListenerHelper;
import io.silverware.microservices.providers.cluster.internal.JgroupsMessageReceiver;
import io.silverware.microservices.providers.cluster.internal.JgroupsMessageSender;
import io.silverware.microservices.providers.cluster.internal.exception.SilverWareClusteringException;
import static io.silverware.microservices.providers.cluster.internal.exception.SilverWareClusteringException.SilverWareClusteringError.JGROUPS_ERROR;
import io.silverware.microservices.providers.cluster.internal.message.KnownImplementation;
import io.silverware.microservices.providers.cluster.internal.message.response.MicroserviceSearchResponse;
import io.silverware.microservices.silver.ClusterSilverService;
import io.silverware.microservices.silver.cluster.RemoteServiceHandlesStore;
import io.silverware.microservices.silver.cluster.ServiceHandle;
import static java.util.Collections.emptySet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * @author Slavomir Krupa (slavomir.krupa@gmail.com)
 * @author <a href="mailto:marvenec@gmail.com">Martin Večeřa</a>
 */
public class ClusterMicroserviceProvider implements MicroserviceProvider, ClusterSilverService {

   private static final Logger log = LogManager.getLogger(ClusterMicroserviceProvider.class);

   private Context context;
   private RemoteServiceHandlesStore remoteServiceHandlesStore;
   private Map<MicroserviceMetaData, Set<Address>> alreadyQueriedAdresses = new HashMap<>();

   private JgroupsMessageSender sender;
   private MessageDispatcher messageDispatcher;


   @Override
   public void initialize(final Context context) {
      try {
         // do some basic initialization
         this.context = context;
         this.remoteServiceHandlesStore = context.getRemoteServiceHandlesStore();
         this.alreadyQueriedAdresses = new HashMap<>();
         context.getProperties().putIfAbsent(CLUSTER_GROUP, "SilverWare");
         context.getProperties().putIfAbsent(CLUSTER_CONFIGURATION, "udp.xml");
         // get jgroups configuration
         final String clusterGroup = (String) this.context.getProperties().get(CLUSTER_GROUP);
         final String clusterConfiguration = (String) this.context.getProperties().get(CLUSTER_CONFIGURATION);
         log.info("Hello from Cluster microservice provider!");
         log.info("Loading cluster configuration from: {} ", clusterConfiguration);
         JChannel channel = new JChannel(clusterConfiguration);
         JgroupsMessageReceiver receiver = new JgroupsMessageReceiver(KnownImplementation.initializeReponders(context), remoteServiceHandlesStore);
         this.messageDispatcher = new MessageDispatcher(channel, receiver, receiver, receiver);
         this.sender = new JgroupsMessageSender(this.messageDispatcher);
         log.info("Setting cluster group: {} ", clusterGroup);
         channel.connect(clusterGroup);
         receiver.setMyAddress(channel.getAddress());
      } catch (Exception e) {
         log.error("Cluster microservice initialization failed.", e);
         throw new RuntimeException(e);
      }
   }

   @Override
   public Context getContext() {
      return context;
   }

   @Override
   public void run() {
      try {
         while (!Thread.currentThread().isInterrupted()) {

         }
      } catch (final Exception e) {
         log.error("Cluster microservice provider failed.", e);
      } finally {
         try {
            this.messageDispatcher.close();
         } catch (IOException e) {
            throw new SilverWareClusteringException(JGROUPS_ERROR, "Unexpected error while closing MessageDispatcher", e);

         }
         log.info("Bye from Cluster microservice provider!");
      }
   }

   @Override
   public Set<Object> lookupMicroservice(final MicroserviceMetaData metaData) {
      try {
         Set<Address> addressesForMetadata = alreadyQueriedAdresses.getOrDefault(metaData, new HashSet<>());
         this.sender.sendToClusterAsync(metaData, addressesForMetadata,
                 new FutureListenerHelper<MicroserviceSearchResponse>(rspList -> {
                    try {
                       RspList<MicroserviceSearchResponse> responseRspList = rspList.get(10, TimeUnit.SECONDS);
                       log.trace("Response retrieved!  {}", responseRspList);
                       Collection<Rsp<MicroserviceSearchResponse>> result = responseRspList.values();
                       log.trace("Size of a responses is : {} ", responseRspList.getResults().size());
                       Set<ServiceHandle> remoteServiceHandles = result.stream()
                               .filter(rsp -> rsp.wasReceived() && rsp.getValue().getResult().canBeUsed())
                               .map((rsp) -> new RemoteServiceHandle(rsp.getSender(), rsp.getValue().getHandle(), sender))
                               .collect(Collectors.toSet());
                       // this is to save jgroups traffic for a given metadata
                       addressesForMetadata.addAll(responseRspList.values().stream().map(Rsp::getSender).collect(Collectors.toSet()));
                       alreadyQueriedAdresses.put(metaData, addressesForMetadata);
                       this.remoteServiceHandlesStore.addHandles(metaData, remoteServiceHandles);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                       throw new SilverWareClusteringException(JGROUPS_ERROR, "Error while looking up microservices.", e);
                    }

                 }));

         return this.remoteServiceHandlesStore.getServices(metaData);
      } catch (Exception e) {
         throw new SilverWareClusteringException(JGROUPS_ERROR, "Error while looking up microservices.", e);
      }
   }

   @Override
   public Set<Object> lookupLocalMicroservice(final MicroserviceMetaData metaData) {
      return emptySet();
   }


}